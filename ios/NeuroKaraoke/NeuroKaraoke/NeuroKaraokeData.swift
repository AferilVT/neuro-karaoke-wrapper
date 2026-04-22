import AVFoundation
import Accelerate
import CommonCrypto
import Combine
import Foundation
import MediaPlayer
import MediaToolbox
import Security
import SwiftUI
import UIKit

enum Singer: String, Codable {
    case neuro = "NEURO"
    case evil = "EVIL"
    case duet = "DUET"
    case other = "OTHER"

    var displayName: String {
        switch self {
        case .neuro:
            return "Neuro-sama"
        case .evil:
            return "Evil Neuro"
        case .duet:
            return "Neuro & Evil"
        case .other:
            return "Other"
        }
    }
}

struct Song: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let artist: String
    let coverURL: URL?
    let audioURL: URL?
    let singer: Singer
    let playlistName: String?
    let artCredit: String?
}

struct Playlist: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let description: String
    let coverURL: URL?
    var previewCoverURLs: [URL]
    let songCount: Int
    var songs: [Song] = []
    var isUserPlaylist = false
}

struct Artist: Identifiable, Hashable {
    let id: String
    let name: String
    let imageURL: URL?
    let songCount: Int
    let summary: String
}

struct CoverDistribution: Hashable {
    let totalSongs: Int
    let neuroCount: Int
    let evilCount: Int
    let duetCount: Int
    let otherCount: Int
}

struct DownloadedSong: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let artist: String
    let coverURL: URL?
    let sourceAudioURL: URL?
    let localAudioPath: String
    let localCoverPath: String?
    let singer: Singer
    let artCredit: String?
    let downloadedAt: Date
    let fileSize: Int64

    var song: Song {
        Song(
            id: id,
            title: title,
            artist: artist,
            coverURL: localCoverPath.map(URL.init(fileURLWithPath:)) ?? coverURL,
            audioURL: URL(fileURLWithPath: localAudioPath),
            singer: singer,
            playlistName: "Downloads",
            artCredit: artCredit
        )
    }
}

struct RadioSong: Identifiable, Hashable, Codable {
    struct CoverArt: Hashable, Codable {
        let cloudflareId: String?
        let absolutePath: String?
        let credit: String?
    }

    let id: String
    let title: String
    let originalArtists: [String]
    let coverArtists: [String]
    let duration: Int
    let coverArt: CoverArt?

    var coverURL: URL? {
        if let cloudflareId = coverArt?.cloudflareId, !cloudflareId.isEmpty {
            return URL(string: "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/\(cloudflareId)/public")
        }
        if let absolutePath = coverArt?.absolutePath, !absolutePath.isEmpty {
            return URL(string: absolutePath)
        }
        return nil
    }

    var song: Song {
        Song(
            id: "radio_\(id)",
            title: title,
            artist: originalArtists.joined(separator: ", ").isEmpty ? "Unknown Artist" : originalArtists.joined(separator: ", "),
            coverURL: coverURL,
            audioURL: URL(string: NeuroKaraokeAPI.radioStreamURL),
            singer: RadioSong.singer(from: coverArtists),
            playlistName: "Radio",
            artCredit: coverArt?.credit
        )
    }

    private static func singer(from artists: [String]) -> Singer {
        let value = artists.joined(separator: " ").localizedLowercase
        if value.contains("evil") && value.contains("neuro") {
            return .duet
        }
        if value.contains("evil") {
            return .evil
        }
        if value.isEmpty {
            return .other
        }
        return .neuro
    }
}

struct RadioState: Hashable, Codable {
    let current: RadioSong?
    let upcoming: [RadioSong]
    let history: [RadioSong]
    let listenerCount: Int
    let offline: Bool
}

struct SetupProgress {
    let fraction: Double
    let status: String
    let detail: String
}

struct CatalogCache: Codable {
    let playlists: [Playlist]
    let songs: [Song]
}

struct EqualizerBand: Hashable, Codable, Identifiable {
    let index: Int
    let centerFrequency: Int
    let minLevel: Int
    let maxLevel: Int
    var currentLevel: Int

    var id: Int { index }
}

struct EqualizerPreset: Hashable, Codable, Identifiable {
    let index: Int
    let name: String
    let bandLevels: [Int]

    var id: Int { index }
}

struct AudioEffectsState: Hashable, Codable {
    var isEnabled = false
    var bands: [EqualizerBand] = []
    var presets: [EqualizerPreset] = []
    var currentPresetIndex = -1
    var isAvailable = false
    var bassBoostEnabled = false
    var bassBoostStrength = 0
    var bassBoostAvailable = false
}

private struct BiquadCoefficients {
    let b0: Float
    let b1: Float
    let b2: Float
    let a1: Float
    let a2: Float

    static func peaking(sampleRate: Double, frequency: Double, gainDB: Double, q: Double = 1.0) -> BiquadCoefficients {
        let clampedFrequency = min(max(frequency, 20), sampleRate * 0.45)
        let a = pow(10, gainDB / 40)
        let omega = 2 * Double.pi * clampedFrequency / sampleRate
        let alpha = sin(omega) / (2 * q)
        let cosine = cos(omega)

        let b0 = 1 + alpha * a
        let b1 = -2 * cosine
        let b2 = 1 - alpha * a
        let a0 = 1 + alpha / a
        let a1 = -2 * cosine
        let a2 = 1 - alpha / a

        return BiquadCoefficients(
            b0: Float(b0 / a0),
            b1: Float(b1 / a0),
            b2: Float(b2 / a0),
            a1: Float(a1 / a0),
            a2: Float(a2 / a0)
        )
    }

    static func lowShelf(sampleRate: Double, frequency: Double, gainDB: Double, slope: Double = 1.0) -> BiquadCoefficients {
        let clampedFrequency = min(max(frequency, 20), sampleRate * 0.45)
        let a = pow(10, gainDB / 40)
        let omega = 2 * Double.pi * clampedFrequency / sampleRate
        let sine = sin(omega)
        let cosine = cos(omega)
        let alpha = sine / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
        let beta = 2 * sqrt(a) * alpha

        let b0 = a * ((a + 1) - (a - 1) * cosine + beta)
        let b1 = 2 * a * ((a - 1) - (a + 1) * cosine)
        let b2 = a * ((a + 1) - (a - 1) * cosine - beta)
        let a0 = (a + 1) + (a - 1) * cosine + beta
        let a1 = -2 * ((a - 1) + (a + 1) * cosine)
        let a2 = (a + 1) + (a - 1) * cosine - beta

        return BiquadCoefficients(
            b0: Float(b0 / a0),
            b1: Float(b1 / a0),
            b2: Float(b2 / a0),
            a1: Float(a1 / a0),
            a2: Float(a2 / a0)
        )
    }
}

private struct BiquadState {
    var x1: Float = 0
    var x2: Float = 0
    var y1: Float = 0
    var y2: Float = 0

    mutating func process(_ sample: Float, coefficients: BiquadCoefficients) -> Float {
        let output =
            coefficients.b0 * sample +
            coefficients.b1 * x1 +
            coefficients.b2 * x2 -
            coefficients.a1 * y1 -
            coefficients.a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = output
        return output
    }
}

private final class RadioAudioProcessor {
    private let lock = NSLock()
    private var coefficients: [BiquadCoefficients] = []
    private var channelStates: [[BiquadState]] = []
    private var currentEffects = AudioEffectsState()
    private var sampleRate: Double = 44_100
    private var isFloatFormat = false

    func update(effects: AudioEffectsState) {
        lock.lock()
        currentEffects = effects
        rebuildCoefficientsLocked()
        channelStates = channelStates.map { _ in Array(repeating: BiquadState(), count: coefficients.count) }
        lock.unlock()
    }

    func prepare(with format: AudioStreamBasicDescription) {
        lock.lock()
        sampleRate = max(format.mSampleRate, 44_100)
        isFloatFormat = format.mBitsPerChannel == 32 && (format.mFormatFlags & kAudioFormatFlagIsFloat) != 0
        let channelCount = max(Int(format.mChannelsPerFrame), 1)
        rebuildCoefficientsLocked()
        channelStates = Array(repeating: Array(repeating: BiquadState(), count: coefficients.count), count: channelCount)
        lock.unlock()
    }

    func reset() {
        lock.lock()
        channelStates = channelStates.map { _ in Array(repeating: BiquadState(), count: coefficients.count) }
        lock.unlock()
    }

    func process(bufferList: UnsafeMutablePointer<AudioBufferList>, frameCount: Int) {
        lock.lock()
        defer { lock.unlock() }

        guard isFloatFormat, !coefficients.isEmpty else { return }

        var globalChannelIndex = 0
        let buffers = UnsafeMutableAudioBufferListPointer(bufferList)

        for buffer in buffers {
            guard let data = buffer.mData else {
                globalChannelIndex += Int(max(buffer.mNumberChannels, 1))
                continue
            }

            let channelCount = max(Int(buffer.mNumberChannels), 1)
            let samples = data.assumingMemoryBound(to: Float.self)

            if channelCount == 1 {
                for frame in 0..<frameCount {
                    var output = samples[frame]
                    for index in coefficients.indices {
                        output = channelStates[globalChannelIndex][index].process(output, coefficients: coefficients[index])
                    }
                    samples[frame] = output
                }
                globalChannelIndex += 1
                continue
            }

            for channel in 0..<channelCount {
                let stateIndex = globalChannelIndex + channel
                for frame in 0..<frameCount {
                    let sampleIndex = frame * channelCount + channel
                    var output = samples[sampleIndex]
                    for index in coefficients.indices {
                        output = channelStates[stateIndex][index].process(output, coefficients: coefficients[index])
                    }
                    samples[sampleIndex] = output
                }
            }
            globalChannelIndex += channelCount
        }
    }

    private func rebuildCoefficientsLocked() {
        var updated: [BiquadCoefficients] = []

        if currentEffects.isEnabled {
            for band in currentEffects.bands {
                let gainDB = Double(band.currentLevel) / 100
                guard abs(gainDB) > 0.01 else { continue }
                updated.append(
                    .peaking(
                        sampleRate: sampleRate,
                        frequency: Double(band.centerFrequency) / 1000,
                        gainDB: gainDB
                    )
                )
            }
        }

        if currentEffects.bassBoostEnabled {
            let gainDB = Double(currentEffects.bassBoostStrength) / 100
            if abs(gainDB) > 0.01 {
                updated.append(
                    .lowShelf(
                        sampleRate: sampleRate,
                        frequency: 120,
                        gainDB: gainDB
                    )
                )
            }
        }

        coefficients = updated
    }
}

private func makeRadioProcessingTap(processor: RadioAudioProcessor) -> MTAudioProcessingTap? {
    var callbacks = MTAudioProcessingTapCallbacks(
        version: kMTAudioProcessingTapCallbacksVersion_0,
        clientInfo: UnsafeMutableRawPointer(Unmanaged.passUnretained(processor).toOpaque()),
        init: radioTapInit,
        finalize: radioTapFinalize,
        prepare: radioTapPrepare,
        unprepare: radioTapUnprepare,
        process: radioTapProcess
    )

    var tap: MTAudioProcessingTap?
    let status = MTAudioProcessingTapCreate(
        kCFAllocatorDefault,
        &callbacks,
        kMTAudioProcessingTapCreationFlag_PostEffects,
        &tap
    )

    guard status == noErr else { return nil }
    return tap
}

private func radioTapInit(
    tap: MTAudioProcessingTap,
    clientInfo: UnsafeMutableRawPointer?,
    tapStorageOut: UnsafeMutablePointer<UnsafeMutableRawPointer?>
) {
    tapStorageOut.pointee = clientInfo
}

private func radioTapFinalize(tap: MTAudioProcessingTap) {
    let storage = MTAudioProcessingTapGetStorage(tap)
    let processor = Unmanaged<RadioAudioProcessor>.fromOpaque(storage).takeUnretainedValue()
    processor.reset()
}

private func radioTapPrepare(
    tap: MTAudioProcessingTap,
    maxFrames: CMItemCount,
    processingFormat: UnsafePointer<AudioStreamBasicDescription>
) {
    let storage = MTAudioProcessingTapGetStorage(tap)
    let processor = Unmanaged<RadioAudioProcessor>.fromOpaque(storage).takeUnretainedValue()
    processor.prepare(with: processingFormat.pointee)
}

private func radioTapUnprepare(tap: MTAudioProcessingTap) {
    let storage = MTAudioProcessingTapGetStorage(tap)
    let processor = Unmanaged<RadioAudioProcessor>.fromOpaque(storage).takeUnretainedValue()
    processor.reset()
}

private func radioTapProcess(
    tap: MTAudioProcessingTap,
    numberFrames: CMItemCount,
    flags: MTAudioProcessingTapFlags,
    bufferListInOut: UnsafeMutablePointer<AudioBufferList>,
    numberFramesOut: UnsafeMutablePointer<CMItemCount>,
    flagsOut: UnsafeMutablePointer<MTAudioProcessingTapFlags>
) {
    let status = MTAudioProcessingTapGetSourceAudio(
        tap,
        numberFrames,
        bufferListInOut,
        flagsOut,
        nil,
        numberFramesOut
    )

    guard status == noErr else {
        numberFramesOut.pointee = 0
        return
    }

    let storage = MTAudioProcessingTapGetStorage(tap)
    let processor = Unmanaged<RadioAudioProcessor>.fromOpaque(storage).takeUnretainedValue()
    processor.process(bufferList: bufferListInOut, frameCount: Int(numberFramesOut.pointee))
}

private enum PlaybackBackend {
    case none
    case avPlayer
    case audioEngine
}

enum QueueSource: Hashable, Codable {
    case playlist(String)
    case allSongs
    case downloads
    case persistedQueue
    case unknown
}

struct PlaybackState: Codable {
    let currentSong: Song?
    let queue: [Song]
    let currentIndex: Int
    let currentTime: Double
    let duration: Double
    let isRadioPlaying: Bool
    let queueSource: QueueSource
}

enum RepeatMode: String, Codable, CaseIterable {
    case off
    case one
    case all
}

struct LyricLine: Hashable, Codable, Identifiable {
    let timestamp: Double
    let text: String

    var id: String { "\(timestamp)-\(text)" }
}

struct Soundbite: Identifiable, Hashable, Codable {
    let id: String
    let title: String
    let comments: String?
    let duration: Int
    let absolutePath: String?
    let tag: Int
    let audioURL: URL?
    let uploadedAt: String?
    let uploadedBy: String?
    let imageURL: URL?
    let embeddable: Bool
    let playCount: Int

    var displayTitle: String {
        title.replacingOccurrences(of: "_", with: " ")
    }

    var tagLabel: String {
        switch tag {
        case 0: return "NEURO"
        case 1: return "EVIL"
        case 2: return "VEDAL"
        default: return "OTHER"
        }
    }
}

struct User: Hashable, Codable {
    let id: String
    let username: String
    let discriminator: String
    let avatar: String?
    let accessToken: String?
    let apiToken: String?

    var displayName: String {
        discriminator != "0" ? "\(username)#\(discriminator)" : username
    }

    var avatarURL: URL? {
        if let avatar, !avatar.isEmpty {
            return URL(string: "https://cdn.discordapp.com/avatars/\(id)/\(avatar).png")
        }
        let index = abs(Int(id) ?? 0) % 5
        return URL(string: "https://cdn.discordapp.com/embed/avatars/\(index).png")
    }
}

@MainActor
final class AppModel: NSObject, ObservableObject {
    @Published private(set) var setupProgress = SetupProgress(
        fraction: 0,
        status: "Preparing…",
        detail: "Loading bundled playlist catalog"
    )
    @Published private(set) var isReady = false
    @Published private(set) var playlists: [Playlist] = []
    @Published private(set) var allSongs: [Song] = []
    @Published private(set) var artists: [Artist] = []
    @Published private(set) var trendingSongs: [Song] = []
    @Published private(set) var coverDistribution: CoverDistribution?
    @Published private(set) var publicPlaylists: [Playlist] = []
    @Published private(set) var isLoadingPublicPlaylists = false
    @Published private(set) var favoriteSongs: [Song] = []
    @Published private(set) var userPlaylists: [Playlist] = []
    @Published private(set) var downloadedSongs: [DownloadedSong] = []
    @Published private(set) var downloadProgress: [String: Double] = [:]
    @Published private(set) var activePlaylistDownloadIDs: Set<String> = []
    @Published private(set) var radioState: RadioState?
    @Published private(set) var isRadioPlaying = false
    @Published private(set) var currentUser: User?
    @Published private(set) var isSyncingLibrary = false
    @Published private(set) var currentSong: Song?
    @Published private(set) var isPlaying = false
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var repeatMode: RepeatMode = .off
    @Published private(set) var isShuffleEnabled = false
    @Published private(set) var lyricLines: [LyricLine] = []
    @Published private(set) var isLoadingLyrics = false
    @Published private(set) var sleepTimerEndTime: Date?
    @Published private(set) var sleepTimerRemaining: TimeInterval = 0
    @Published private(set) var sleepTimerEndOfSong = false
    @Published private(set) var soundbites: [Soundbite] = []
    @Published private(set) var totalSoundbites = 0
    @Published private(set) var isLoadingSoundbites = false
    @Published private(set) var isLoadingMoreSoundbites = false
    @Published private(set) var currentSoundbitePage = 1
    @Published private(set) var playingSoundbiteID: String?
    @Published private(set) var audioEffects = AudioEffectsState()
    @Published var selectedTab: AppTab = .home
    @Published var errorMessage: String?

    private let api = NeuroKaraokeAPI()
    private let storage = LocalLibraryStore()
    private let player = AVPlayer()
    private let audioEngine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private let equalizerNode = AVAudioUnitEQ(numberOfBands: 6)
    private let radioAudioProcessor = RadioAudioProcessor()
    private var queue: [Song] = []
    private var currentIndex = 0
    private var didStart = false
    private var radioRefreshTask: Task<Void, Never>?
    private var pendingCodeVerifier: String?
    private var lastPersistedPlaybackSecond = -1
    private var sleepTimerTask: Task<Void, Never>?
    private var lastNowPlayingArtworkURL: URL?
    private var cachedNowPlayingArtwork: MPMediaItemArtwork?
    private var catalogRefreshTask: Task<Void, Never>?
    private var playbackObserverTask: Task<Void, Never>?
    private var playbackBackend: PlaybackBackend = .none
    private var currentQueueSource: QueueSource = .unknown
    private var currentAudioFile: AVAudioFile?
    private var currentPlaybackURL: URL?
    private var engineScheduledStartTime: Double = 0
    private var pausedEngineTime: Double = 0
    private var audioEnginePlaybackToken: UUID?
    private var shouldResumeAfterInterruption = false
    private var playerItemStatusObservation: NSKeyValueObservation?
    private var playerTimeControlStatusObservation: NSKeyValueObservation?
    private var radioRetryAttempts = 0

    deinit {
        playbackObserverTask?.cancel()
        playerItemStatusObservation?.invalidate()
        playerTimeControlStatusObservation?.invalidate()
    }

    func start() async {
        guard !didStart else { return }
        didStart = true

        // Let SwiftUI present the setup view before startup work begins on the main actor.
        await Task.yield()

        configureAVPlayer()
        configureAudioSession()
        configureAudioEngine()
        configureAudioSessionNotifications()
        configureRemoteCommands()
        configurePlaybackNotifications()
        configurePlayerObserver()
        loadPersistedLibrary()
        loadAudioEffects()
        restorePlaybackState()

        // Yield once more before reading and decoding the initial catalog.
        await Task.yield()

        do {
            let seedPlaylists = try api.loadSeedPlaylists()
            let cachedCatalog = storage.loadCatalogCache()

            if let cachedCatalog, !cachedCatalog.playlists.isEmpty {
                playlists = cachedCatalog.playlists
                allSongs = cachedCatalog.songs
                updateProgress(0.65, "Loading cached catalog…", "\(cachedCatalog.songs.count) songs ready")
            } else {
                playlists = seedPlaylists
                    .filter { !$0.title.isEmpty || $0.songCount > 0 || $0.coverURL != nil }
                    .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedDescending }
                allSongs = []
                updateProgress(0.2, "Preparing catalog…", "Setlists will continue loading in the background")
            }

            await performStartupWarmup()
            isReady = true
            catalogRefreshTask = Task { [weak self] in
                await self?.refreshCatalog(seedPlaylists: seedPlaylists)
            }
        } catch {
            errorMessage = error.localizedDescription
            isReady = true
        }
    }

    private func performStartupWarmup() async {
        updateProgress(0.82, "Warming up app…", "Preloading soundbites, radio, and explore")

        async let soundbitesTask = fetchInitialSoundbiteBatch(search: nil)
        async let radioStateTask = api.fetchRadioState()
        async let publicPlaylistsTask = api.fetchPublicPlaylists()

        if let soundbiteBatch = try? await soundbitesTask {
            soundbites = soundbiteBatch.items
            currentSoundbitePage = soundbiteBatch.page
            totalSoundbites = soundbiteBatch.totalCount
        }

        if let radioState = try? await radioStateTask {
            self.radioState = radioState
        }

        if let publicPlaylists = try? await publicPlaylistsTask {
            self.publicPlaylists = publicPlaylists
            isLoadingPublicPlaylists = false
        }
    }

    private func refreshCatalog(seedPlaylists: [Playlist]) async {
        updateProgress(0.25, "Loading setlists…", "Fetching \(seedPlaylists.count) playlists")

        async let artistsTask = api.fetchArtists()
        async let distributionTask = api.fetchCoverDistribution()
        async let trendingTask = api.fetchTrendingSongs()
        async let publicPlaylistsTask = api.fetchPublicPlaylists()

        let hydratedPlaylists = await withTaskGroup(of: (Int, Playlist?).self) { group in
            for (index, seed) in seedPlaylists.enumerated() {
                group.addTask { [api] in
                    do {
                        return (index, try await api.fetchPlaylist(id: seed.id, fallback: seed))
                    } catch {
                        return (index, nil)
                    }
                }
            }

            var collected = Array<Playlist?>(repeating: nil, count: seedPlaylists.count)
            var completedCount = 0

            for await (index, playlist) in group {
                collected[index] = playlist
                completedCount += 1

                if let playlist {
                    let fraction = 0.25 + (Double(completedCount) / Double(max(seedPlaylists.count, 1))) * 0.55
                    let displayTitle = playlist.title.isEmpty ? "Playlist \(completedCount)" : playlist.title
                    updateProgress(fraction, "Loading setlists…", displayTitle)
                }
            }

            return collected.compactMap { $0 }
        }

        if hydratedPlaylists.count < seedPlaylists.count {
            errorMessage = "Some playlists could not be loaded."
        }

        let uniqueSongs = Array(
            Dictionary(hydratedPlaylists.flatMap(\.songs).map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
                .values
        )
        .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }

        playlists = hydratedPlaylists
            .filter { !$0.songs.isEmpty || !$0.title.isEmpty }
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedDescending }
        allSongs = uniqueSongs
        repairQueueAfterCatalogRefresh()
        storage.saveCatalogCache(CatalogCache(playlists: playlists, songs: allSongs))

        updateProgress(0.88, "Syncing discovery data…", "Loading artists and stats")
        artists = (try? await artistsTask) ?? []
        coverDistribution = try? await distributionTask
        trendingSongs = (try? await trendingTask) ?? Array(uniqueSongs.prefix(8))
        publicPlaylists = (try? await publicPlaylistsTask) ?? []
        isLoadingPublicPlaylists = false

        updateProgress(1.0, "Ready", "\(uniqueSongs.count) songs available")
    }

    func songs(for playlist: Playlist) -> [Song] {
        playlists.first(where: { $0.id == playlist.id })?.songs ?? playlist.songs
    }

    func songs(for artist: Artist) -> [Song] {
        allSongs.filter { song in
            song.artist.localizedCaseInsensitiveContains(artist.name)
        }
    }

    func loadPublicPlaylists() async {
        isLoadingPublicPlaylists = true
        defer { isLoadingPublicPlaylists = false }
        do {
            publicPlaylists = try await api.fetchPublicPlaylists()
        } catch {
            publicPlaylists = []
        }
    }

    var queueSongs: [Song] {
        queue
    }

    var activeLyricIndex: Int? {
        guard !lyricLines.isEmpty else { return nil }
        let index = lyricLines.lastIndex { $0.timestamp <= currentTime }
        return index
    }

    func filteredSongs(query: String) -> [Song] {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return allSongs
        }

        let normalizedQuery = query.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
        return allSongs.filter { song in
            song.title.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).contains(normalizedQuery) ||
            song.artist.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).contains(normalizedQuery) ||
            (song.playlistName?.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).contains(normalizedQuery) ?? false)
        }
    }

    func play(_ song: Song, in songs: [Song]? = nil, source: QueueSource? = nil) {
        stopRadioPlayback(clearCurrent: false)
        queue = songs ?? allSongs
        currentQueueSource = source ?? inferredQueueSource(for: songs)
        if let queueIndex = queue.firstIndex(of: song) {
            currentIndex = queueIndex
        } else {
            queue = [song]
            currentIndex = 0
        }

        currentSong = queue[currentIndex]
        lyricLines = []
        Task {
            await playAudioEngineSong(song)
            await loadLyrics(for: song)
        }
    }

    func togglePlayback() {
        switch playbackBackend {
        case .audioEngine:
            toggleAudioEnginePlayback()
        case .avPlayer:
            if isPlaying {
                player.pause()
            } else {
                activateAudioSession()
                player.play()
            }
            isPlaying.toggle()
        case .none:
            resumeCurrentPlayback()
        }
        updateNowPlayingInfo()
        persistPlaybackState()
    }

    func seek(to progress: Double) {
        guard duration > 0 else { return }
        let seconds = duration * progress
        switch playbackBackend {
        case .audioEngine:
            seekAudioEngine(to: seconds)
        case .avPlayer:
            player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
        case .none:
            break
        }
        currentTime = seconds
        updateNowPlayingInfo()
        persistPlaybackState()
    }

    func playNext() {
        if queue.isEmpty {
            guard rebuildQueueForCurrentSong() else { return }
        }
        if isShuffleEnabled, queue.count > 1 {
            let candidates = queue.indices.filter { $0 != currentIndex }
            currentIndex = candidates.randomElement() ?? currentIndex
        } else if currentIndex + 1 < queue.count {
            currentIndex += 1
        } else if repeatMode == .all {
            currentIndex = 0
        } else {
            _ = playRandomSongFromAllSongs()
            return
        }
        play(queue[currentIndex], in: queue)
    }

    func playPrevious() {
        if queue.isEmpty {
            guard rebuildQueueForCurrentSong() else { return }
        }
        if currentTime > 5 {
            seek(to: 0)
            return
        }
        if isShuffleEnabled, queue.count > 1 {
            let candidates = queue.indices.filter { $0 != currentIndex }
            currentIndex = candidates.randomElement() ?? currentIndex
        } else {
            currentIndex = max(currentIndex - 1, 0)
        }
        play(queue[currentIndex], in: queue)
    }

    func cycleRepeatMode() {
        switch repeatMode {
        case .off:
            repeatMode = .all
        case .all:
            repeatMode = .one
        case .one:
            repeatMode = .off
        }
        persistPlaybackState()
    }

    func toggleShuffle() {
        isShuffleEnabled.toggle()
        persistPlaybackState()
    }

    func playQueueSong(_ song: Song) {
        play(song, in: queue, source: currentQueueSource)
    }

    func moveQueueSongs(from source: IndexSet, to destination: Int) {
        guard !queue.isEmpty else { return }

        let currentSongID = currentSong?.id
        queue.move(fromOffsets: source, toOffset: destination)

        if let currentSongID,
           let updatedIndex = queue.firstIndex(where: { $0.id == currentSongID }) {
            currentIndex = updatedIndex
        } else {
            currentIndex = min(currentIndex, max(queue.count - 1, 0))
        }

        persistPlaybackState()
    }

    func removeQueueSongs(at offsets: IndexSet) {
        guard !queue.isEmpty else { return }

        let removingCurrentSong = offsets.contains(currentIndex)
        let currentSongID = currentSong?.id
        queue.remove(atOffsets: offsets)

        if queue.isEmpty {
            pauseActivePlayback()
            stopEnginePlayback(resetState: true)
            player.replaceCurrentItem(with: nil)
            currentSong = nil
            currentIndex = 0
            currentTime = 0
            duration = 0
            isPlaying = false
            playbackBackend = .none
            updateNowPlayingInfo()
            persistPlaybackState()
            return
        }

        if removingCurrentSong {
            currentIndex = min(currentIndex, max(queue.count - 1, 0))
            play(queue[currentIndex], in: queue)
            return
        }

        if let currentSongID,
           let updatedIndex = queue.firstIndex(where: { $0.id == currentSongID }) {
            currentIndex = updatedIndex
        } else {
            currentIndex = min(currentIndex, max(queue.count - 1, 0))
        }

        persistPlaybackState()
    }

    func setAudioEffectsEnabled(_ enabled: Bool) {
        audioEffects.isEnabled = enabled
        applyAudioEffects()
        persistAudioEffects()
    }

    func setEqualizerBandLevel(_ bandIndex: Int, level: Int) {
        guard let index = audioEffects.bands.firstIndex(where: { $0.index == bandIndex }) else { return }
        let band = audioEffects.bands[index]
        audioEffects.bands[index].currentLevel = level.clamped(to: band.minLevel...band.maxLevel)
        audioEffects.currentPresetIndex = -1
        applyAudioEffects()
        persistAudioEffects()
    }

    func useEqualizerPreset(_ preset: EqualizerPreset) {
        guard preset.bandLevels.count == audioEffects.bands.count else { return }
        for index in audioEffects.bands.indices {
            let band = audioEffects.bands[index]
            audioEffects.bands[index].currentLevel = preset.bandLevels[index].clamped(to: band.minLevel...band.maxLevel)
        }
        audioEffects.currentPresetIndex = preset.index
        applyAudioEffects()
        persistAudioEffects()
    }

    func resetEqualizerToFlat() {
        for index in audioEffects.bands.indices {
            audioEffects.bands[index].currentLevel = 0
        }
        audioEffects.currentPresetIndex = -1
        applyAudioEffects()
        persistAudioEffects()
    }

    func setBassBoostEnabled(_ enabled: Bool) {
        audioEffects.bassBoostEnabled = enabled
        applyAudioEffects()
        persistAudioEffects()
    }

    func setBassBoostStrength(_ strength: Int) {
        audioEffects.bassBoostStrength = strength.clamped(to: 0...1000)
        applyAudioEffects()
        persistAudioEffects()
    }

    func setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        let endTime = Date().addingTimeInterval(TimeInterval(minutes * 60))
        sleepTimerEndTime = endTime
        sleepTimerRemaining = TimeInterval(minutes * 60)
        sleepTimerEndOfSong = false

        sleepTimerTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                let remaining = endTime.timeIntervalSinceNow
                await MainActor.run {
                    if remaining <= 0 {
                        self.pauseActivePlayback()
                        self.isPlaying = false
                        self.sleepTimerEndTime = nil
                        self.sleepTimerRemaining = 0
                    } else {
                        self.sleepTimerRemaining = remaining
                    }
                }
                if remaining <= 0 { break }
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    func cancelSleepTimer() {
        sleepTimerTask?.cancel()
        sleepTimerTask = nil
        sleepTimerEndTime = nil
        sleepTimerRemaining = 0
        sleepTimerEndOfSong = false
    }

    func setSleepTimerEndOfSong() {
        cancelSleepTimer()
        sleepTimerEndOfSong = true
    }

    func loadSoundbites(search: String = "", reset: Bool = true) async {
        if reset {
            isLoadingSoundbites = true
            currentSoundbitePage = 1
        } else {
            isLoadingMoreSoundbites = true
        }

        defer {
            isLoadingSoundbites = false
            isLoadingMoreSoundbites = false
        }

        do {
            if reset {
                if search.isEmpty {
                    let initialBatch = try await fetchInitialSoundbiteBatch(search: nil)
                    soundbites = initialBatch.items
                    currentSoundbitePage = initialBatch.page
                    totalSoundbites = initialBatch.totalCount
                    return
                }

                let response = try await api.fetchSoundbites(page: 1, pageSize: 30, search: search)
                soundbites = response.items
                currentSoundbitePage = response.page
                totalSoundbites = response.totalCount
            } else {
                let page = currentSoundbitePage + 1
                let response = try await api.fetchSoundbites(page: page, pageSize: 30, search: search.isEmpty ? nil : search)
                soundbites += response.items
                currentSoundbitePage = response.page
                totalSoundbites = response.totalCount
            }
        } catch {
            errorMessage = "Failed to load soundbites."
        }
    }

    private func fetchInitialSoundbiteBatch(search: String?) async throws -> (items: [Soundbite], page: Int, totalCount: Int) {
        let normalizedSearch = search?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !normalizedSearch.isEmpty {
            let response = try await api.fetchSoundbites(page: 1, pageSize: 30, search: normalizedSearch)
            return (response.items, response.page, response.totalCount)
        }

        let initialPageCount = 6
        let targetTags: Set<Int> = [0, 1, 2, 3]
        var combinedItems: [Soundbite] = []
        var seenTags = Set<Int>()
        var lastPage = 1
        var totalCount = 0

        for page in 1...initialPageCount {
            let response = try await api.fetchSoundbites(page: page, pageSize: 30, search: nil)
            combinedItems += response.items
            seenTags.formUnion(response.items.map(\.tag))
            lastPage = response.page
            totalCount = response.totalCount

            if combinedItems.count >= totalCount || targetTags.isSubset(of: seenTags) {
                break
            }
        }

        let uniqueItems = Array(
            Dictionary(combinedItems.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first }).values
        )
        return (uniqueItems, lastPage, totalCount)
    }

    func playSoundbite(_ soundbite: Soundbite) {
        if playingSoundbiteID == soundbite.id {
            pauseActivePlayback()
            stopEnginePlayback(resetState: true)
            playingSoundbiteID = nil
            isPlaying = false
            return
        }

        guard let url = soundbite.audioURL else { return }
        queue = []
        currentIndex = 0
        currentQueueSource = .persistedQueue
        playingSoundbiteID = soundbite.id
        currentSong = Song(
            id: "soundbite_\(soundbite.id)",
            title: soundbite.displayTitle,
            artist: soundbite.uploadedBy ?? soundbite.tagLabel,
            coverURL: soundbite.imageURL,
            audioURL: url,
            singer: soundbite.tag == 1 ? .evil : .neuro,
            playlistName: "Soundbites",
            artCredit: nil
        )
        Task {
            await playAudioEngineSong(currentSong!)
        }
    }

    func toggleFavorite(_ song: Song) {
        if let index = favoriteSongs.firstIndex(of: song) {
            favoriteSongs.remove(at: index)
        } else {
            favoriteSongs.insert(song, at: 0)
        }
        persistLibrary()
    }

    func isFavorite(_ song: Song) -> Bool {
        favoriteSongs.contains(song)
    }

    func createPlaylist(name: String, description: String) {
        let playlist = Playlist(
            id: "user_\(UUID().uuidString)",
            title: name,
            description: description,
            coverURL: nil,
            previewCoverURLs: [],
            songCount: 0,
            songs: [],
            isUserPlaylist: true
        )
        userPlaylists.insert(playlist, at: 0)
        persistLibrary()
    }

    func deletePlaylist(_ playlist: Playlist) {
        userPlaylists.removeAll { $0.id == playlist.id }
        persistLibrary()
    }

    func addSong(_ song: Song, to playlist: Playlist) {
        guard let index = userPlaylists.firstIndex(where: { $0.id == playlist.id }) else { return }
        guard !userPlaylists[index].songs.contains(song) else { return }
        userPlaylists[index].songs.append(song)
        userPlaylists[index].previewCoverURLs = Array(userPlaylists[index].songs.compactMap(\.coverURL).prefix(4))
        persistLibrary()
    }

    func removeSong(_ song: Song, from playlist: Playlist) {
        guard let index = userPlaylists.firstIndex(where: { $0.id == playlist.id }) else { return }
        userPlaylists[index].songs.removeAll { $0.id == song.id }
        userPlaylists[index].previewCoverURLs = Array(userPlaylists[index].songs.compactMap(\.coverURL).prefix(4))
        persistLibrary()
    }

    func downloadSong(_ song: Song) async {
        guard let remoteAudioURL = song.audioURL, !isDownloaded(song.id) else { return }
        do {
            updateDownloadProgress(song.id, 0.05)
            let localAudioURL = try await storage.downloadFile(
                from: remoteAudioURL,
                fileName: "\(song.id).mp3"
            ) { progress in
                Task { @MainActor in
                    guard !self.isDownloaded(song.id) else { return }
                    self.updateDownloadProgress(song.id, progress * 0.9)
                }
            }

            var localCoverPath: String?
            if let coverURL = song.coverURL {
                do {
                    let localCoverURL = try await storage.downloadFile(
                        from: coverURL,
                        fileName: "\(song.id).jpg",
                        inCoversDirectory: true
                    ) { progress in
                        Task { @MainActor in
                            guard !self.isDownloaded(song.id) else { return }
                            self.updateDownloadProgress(song.id, 0.9 + progress * 0.1)
                        }
                    }
                    localCoverPath = localCoverURL.path
                } catch {
                    localCoverPath = nil
                }
            }

            let fileSize = Int64((try? localAudioURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
            let downloaded = DownloadedSong(
                id: song.id,
                title: song.title,
                artist: song.artist,
                coverURL: song.coverURL,
                sourceAudioURL: remoteAudioURL,
                localAudioPath: localAudioURL.path,
                localCoverPath: localCoverPath,
                singer: song.singer,
                artCredit: song.artCredit,
                downloadedAt: Date(),
                fileSize: fileSize
            )
            downloadedSongs.insert(downloaded, at: 0)
            persistLibrary()
        } catch {
            errorMessage = "Failed to download \(song.title)."
        }
        downloadProgress.removeValue(forKey: song.id)
    }

    func downloadPlaylist(_ playlist: Playlist) async {
        let songs = songs(for: playlist)
        await downloadSongs(songs, playlistID: playlist.id)
    }

    func downloadUserPlaylist(_ playlist: Playlist) async {
        let songs = userPlaylists.first(where: { $0.id == playlist.id })?.songs ?? playlist.songs
        await downloadSongs(songs, playlistID: playlist.id)
    }

    func downloadedSongCount(for playlist: Playlist) -> Int {
        songs(for: playlist).reduce(into: 0) { count, song in
            if isDownloaded(song.id) {
                count += 1
            }
        }
    }

    func downloadedSongCount(forUserPlaylist playlist: Playlist) -> Int {
        let songs = userPlaylists.first(where: { $0.id == playlist.id })?.songs ?? playlist.songs
        return songs.reduce(into: 0) { count, song in
            if isDownloaded(song.id) {
                count += 1
            }
        }
    }

    func reconcileDownloadsAndCaches() {
        let reconciledDownloads = storage.reconcileDownloads(downloadedSongs)
        let didChangeDownloads = reconciledDownloads != downloadedSongs
        downloadedSongs = reconciledDownloads
        storage.cleanupPlaybackCache(olderThan: 7 * 24 * 60 * 60)
        if didChangeDownloads {
            persistLibrary()
        }
    }

    func removeDownload(_ download: DownloadedSong) {
        storage.removeDownload(download)
        downloadedSongs.removeAll { $0.id == download.id }
        persistLibrary()
    }

    func removeDownload(songID: String) {
        guard let download = downloadedSongs.first(where: { $0.id == songID }) else { return }
        removeDownload(download)
    }

    func removeAllDownloads() {
        downloadedSongs.forEach(storage.removeDownload)
        downloadedSongs.removeAll()
        persistLibrary()
    }

    func isDownloaded(_ songID: String) -> Bool {
        downloadedSongs.contains { $0.id == songID }
    }

    func playDownload(_ download: DownloadedSong) {
        play(download.song, in: downloadedSongs.map(\.song), source: .downloads)
    }

    func totalDownloadedSizeDescription() -> String {
        ByteCountFormatter.string(fromByteCount: downloadedSongs.reduce(0) { $0 + $1.fileSize }, countStyle: .file)
    }

    func refreshRadioState() async {
        do {
            let latestState = try await api.fetchRadioState()
            radioState = latestState
            syncRadioNowPlaying(with: latestState)
        } catch {
            if radioState == nil {
                errorMessage = "Failed to load radio state."
            }
        }
    }

    func startRadioPolling() {
        guard radioRefreshTask == nil else { return }
        radioRefreshTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                await self.refreshRadioState()
                try? await Task.sleep(for: .seconds(15))
            }
        }
    }

    func stopRadioPolling() {
        radioRefreshTask?.cancel()
        radioRefreshTask = nil
    }

    func playRadio() {
        guard let url = URL(string: NeuroKaraokeAPI.radioStreamURL) else { return }
        stopEnginePlayback(resetState: false)
        queue = []
        currentIndex = 0
        currentQueueSource = .unknown
        startRadioPolling()
        activateAudioSession()
        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset, automaticallyLoadedAssetKeys: [.tracks])
        Task {
            await configureRadioAudioMix(for: item)
        }
        player.replaceCurrentItem(with: item)
        observePlayerItem(item)
        player.play()
        playbackBackend = .avPlayer
        radioRetryAttempts = 0
        currentSong = radioState?.current?.song ?? Song(
            id: "radio_live",
            title: "Neuro Karaoke Radio",
            artist: "Live Stream",
            coverURL: nil,
            audioURL: url,
            singer: .neuro,
            playlistName: "Radio",
            artCredit: nil
        )
        isRadioPlaying = true
        isPlaying = true
        updateNowPlayingInfo()
        persistPlaybackState()
    }

    func stopRadioPlayback(clearCurrent: Bool = true) {
        guard isRadioPlaying else { return }
        player.pause()
        stopRadioPolling()
        radioRetryAttempts = 0
        isRadioPlaying = false
        isPlaying = false
        playbackBackend = .none
        if clearCurrent {
            currentSong = nil
        }
        updateNowPlayingInfo()
        persistPlaybackState()
    }

    func signIn() {
        let verifier = Self.generateCodeVerifier()
        pendingCodeVerifier = verifier
        let challenge = Self.generateCodeChallenge(verifier)

        var components = URLComponents(string: "https://discord.com/oauth2/authorize")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: "1447802634621943850"),
            URLQueryItem(name: "redirect_uri", value: "neurokaraoke://auth"),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "identify"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256")
        ]

        guard let url = components.url else { return }
        UIApplication.shared.open(url)
    }

    func webViewSignInURL() -> URL? {
        URL(string: "https://neurokaraoke.com/login-page")
    }

    @discardableResult
    func handleJwtFromWebView(_ jwt: String) -> Bool {
        guard let user = Self.user(fromJWT: jwt) else {
            errorMessage = "Web sign-in failed."
            return false
        }

        currentUser = user
        storage.saveUser(user)
        Task { await syncLibraryFromServer() }
        return true
    }

    func handleOpenURL(_ url: URL) {
        let isAuthCallback =
            (url.scheme == "neurokaraoke" && url.host == "auth") ||
            (url.scheme == "https" && url.host == "neurokaraoke.com" && url.path == "/app-auth")

        guard isAuthCallback else { return }

        if let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == "code" })?
            .value {
            Task {
                await exchangeCodeForUser(code)
            }
            return
        }

        let queryItems = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let user = User(
            id: queryItems.first(where: { $0.name == "id" })?.value ?? "",
            username: queryItems.first(where: { $0.name == "username" })?.value ?? "",
            discriminator: queryItems.first(where: { $0.name == "discriminator" })?.value ?? "0",
            avatar: queryItems.first(where: { $0.name == "avatar" })?.value,
            accessToken: queryItems.first(where: { $0.name == "token" })?.value,
            apiToken: nil
        )
        guard !user.id.isEmpty, !user.username.isEmpty else { return }
        currentUser = user
        storage.saveUser(user)
        Task { await syncLibraryFromServer() }
    }

    func logout() {
        currentUser = nil
        storage.clearUser()
    }

    func syncLibraryFromServer() async {
        guard let accessToken = currentUser?.apiToken ?? currentUser?.accessToken else { return }
        isSyncingLibrary = true
        defer { isSyncingLibrary = false }

        do {
            async let favoritesTask = api.fetchFavorites(accessToken: accessToken)
            async let playlistsTask = api.fetchUserPlaylists(accessToken: accessToken)
            favoriteSongs = try await favoritesTask
            userPlaylists = try await playlistsTask
            persistLibrary()
        } catch {
            errorMessage = "Library sync failed."
        }
    }

    private func exchangeCodeForUser(_ code: String) async {
        guard let verifier = pendingCodeVerifier else {
            errorMessage = "Sign-in expired. Try again."
            return
        }
        pendingCodeVerifier = nil

        do {
            let user = try await api.exchangeDiscordCode(code: code, verifier: verifier)
            currentUser = user
            storage.saveUser(user)
            await syncLibraryFromServer()
        } catch {
            errorMessage = "Authentication failed."
        }
    }

    private static func user(fromJWT jwt: String) -> User? {
        let parts = jwt.split(separator: ".")
        guard parts.count == 3 else { return nil }

        var payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")

        let padding = payload.count % 4
        if padding > 0 {
            payload += String(repeating: "=", count: 4 - padding)
        }

        guard
            let data = Data(base64Encoded: payload),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return nil
        }

        let userID = object["http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier"] as? String ?? ""
        let username = object["http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name"] as? String ?? ""
        let avatar = (object["urn:discord:avatar"] as? String).flatMap { $0.isEmpty ? nil : $0 }

        guard !userID.isEmpty, !username.isEmpty else { return nil }

        return User(
            id: userID,
            username: username,
            discriminator: "0",
            avatar: avatar,
            accessToken: nil,
            apiToken: jwt
        )
    }

    private func configurePlayerObserver() {
        guard playbackObserverTask == nil else { return }
        playbackObserverTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                await MainActor.run {
                    let currentSeconds: Double
                    let resolvedDuration: Double

                    switch self.playbackBackend {
                    case .audioEngine:
                        currentSeconds = self.currentAudioEngineTime()
                        resolvedDuration = self.currentAudioFile.map { Double($0.length) / $0.processingFormat.sampleRate } ?? self.duration
                    case .avPlayer:
                        let time = self.player.currentTime().seconds
                        currentSeconds = time.isFinite ? time : 0
                        let itemDuration = self.player.currentItem?.duration.seconds ?? 0
                        resolvedDuration = itemDuration.isFinite ? itemDuration : 0
                    case .none:
                        currentSeconds = self.currentTime
                        resolvedDuration = self.duration
                    }

                    self.currentTime = currentSeconds
                    self.duration = resolvedDuration
                    if self.isRadioPlaying,
                       let radioSong = self.radioState?.current?.song,
                       self.currentSong != radioSong {
                        self.currentSong = radioSong
                    }
                    self.updateNowPlayingInfo()
                    let wholeSecond = Int(currentSeconds.rounded(.down))
                    if wholeSecond != self.lastPersistedPlaybackSecond, wholeSecond % 15 == 0 {
                        self.lastPersistedPlaybackSecond = wholeSecond
                        self.persistPlaybackState()
                    }
                }
                try? await Task.sleep(for: .milliseconds(500))
            }
        }
    }

    private func configurePlaybackNotifications() {
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handlePlaybackEnded()
            }
        }

        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemPlaybackStalled,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handlePlayerItemPlaybackStalled()
            }
        }
    }

    private func handlePlaybackEnded() {
        if isRadioPlaying {
            playRadio()
            return
        }

        if currentSong?.id.hasPrefix("soundbite_") == true {
            playbackBackend = .none
            isPlaying = false
            currentTime = duration
            playingSoundbiteID = nil
            updateNowPlayingInfo()
            persistPlaybackState()
            return
        }

        if sleepTimerEndOfSong {
            sleepTimerEndOfSong = false
            pauseActivePlayback()
            isPlaying = false
            updateNowPlayingInfo()
            persistPlaybackState()
            return
        }

        switch repeatMode {
        case .one:
            guard let song = currentSong else { return }
            play(song, in: queue)
        case .all:
            playNext()
        case .off:
            if currentIndex + 1 < queue.count {
                playNext()
            } else if !playRandomSongFromAllSongs() {
                playbackBackend = .none
                isPlaying = false
                updateNowPlayingInfo()
                persistPlaybackState()
            } else {
                return
            }
        }
    }

    private func handlePlayerItemPlaybackStalled() {
        guard isRadioPlaying else { return }
        retryRadioPlaybackAfterFailure()
    }

    private func updateDownloadProgress(_ songID: String, _ progress: Double) {
        downloadProgress[songID] = progress
    }

    private func loadPersistedLibrary() {
        favoriteSongs = storage.loadFavorites()
        userPlaylists = storage.loadPlaylists()
        downloadedSongs = storage.reconcileDownloads(storage.loadDownloads())
        currentUser = storage.loadUser()
        reconcileDownloadsAndCaches()
    }

    private func loadAudioEffects() {
        if let stored = storage.loadAudioEffects() {
            audioEffects = stored
            if audioEffects.bands.isEmpty {
                audioEffects.bands = Self.defaultEqualizerBands
            }
            if audioEffects.presets.isEmpty {
                audioEffects.presets = Self.defaultEqualizerPresets
            }
        } else {
            audioEffects = AudioEffectsState(
                bands: Self.defaultEqualizerBands,
                presets: Self.defaultEqualizerPresets
            )
        }
        audioEffects.isAvailable = true
        audioEffects.bassBoostAvailable = true
    }

    private func persistLibrary() {
        storage.saveFavorites(favoriteSongs)
        storage.savePlaylists(userPlaylists)
        storage.saveDownloads(downloadedSongs)
    }

    private func downloadSongs(_ songs: [Song], playlistID: String) async {
        guard !activePlaylistDownloadIDs.contains(playlistID) else { return }
        activePlaylistDownloadIDs.insert(playlistID)
        defer { activePlaylistDownloadIDs.remove(playlistID) }

        let missingSongs = songs.filter { !isDownloaded($0.id) && $0.audioURL != nil }
        for song in missingSongs {
            await downloadSong(song)
        }
    }

    private func persistAudioEffects() {
        storage.saveAudioEffects(audioEffects)
    }

    private func repairQueueAfterCatalogRefresh() {
        guard let currentSong else { return }

        if let queueIndex = queue.firstIndex(where: { $0.id == currentSong.id }) {
            currentIndex = queueIndex
            return
        }

        _ = rebuildQueueForCurrentSong()
    }

    @discardableResult
    private func rebuildQueueForCurrentSong() -> Bool {
        guard let currentSong else { return false }

        switch currentQueueSource {
        case .playlist(let playlistID):
            if let playlist = playlists.first(where: { $0.id == playlistID }) {
                let playlistSongs = songs(for: playlist)
                if let playlistIndex = playlistSongs.firstIndex(where: { $0.id == currentSong.id }) {
                    queue = playlistSongs
                    currentIndex = playlistIndex
                    return true
                }
            }
        case .downloads:
            let downloadedQueue = downloadedSongs.map(\.song)
            if let restoredIndex = downloadedQueue.firstIndex(where: { $0.id == currentSong.id }) {
                queue = downloadedQueue
                currentIndex = restoredIndex
                return true
            }
        case .persistedQueue:
            if let savedQueue = storage.loadPlaybackState()?.queue,
               let restoredIndex = savedQueue.firstIndex(where: { $0.id == currentSong.id }) {
                queue = savedQueue
                currentIndex = restoredIndex
                return true
            }
        case .allSongs:
            if let restoredIndex = allSongs.firstIndex(where: { $0.id == currentSong.id }) {
                queue = allSongs
                currentIndex = restoredIndex
                return true
            }
        case .unknown:
            break
        }

        if let restoredIndex = allSongs.firstIndex(where: { $0.id == currentSong.id }) {
            queue = allSongs
            currentIndex = restoredIndex
            currentQueueSource = .allSongs
            return true
        }

        return false
    }

    private func inferredQueueSource(for songs: [Song]?) -> QueueSource {
        guard let songs else { return .allSongs }
        if songs == allSongs { return .allSongs }
        if songs.map(\.id) == downloadedSongs.map(\.song.id) { return .downloads }
        return .persistedQueue
    }

    @discardableResult
    private func playRandomSongFromAllSongs() -> Bool {
        let candidates: [Song]
        if let currentSong {
            candidates = allSongs.filter { $0.id != currentSong.id }
        } else {
            candidates = allSongs
        }

        guard let randomSong = candidates.randomElement(), !allSongs.isEmpty else { return false }
        queue = allSongs
        currentIndex = queue.firstIndex(where: { $0.id == randomSong.id }) ?? 0
        play(randomSong, in: queue)
        return true
    }

    private func resumeCurrentPlayback() {
        if isRadioPlaying {
            playRadio()
            return
        }

        guard let song = currentSong else { return }
        let resumeTime = max(currentTime, 0)

        Task {
            do {
                guard let audioURL = song.audioURL else { return }
                let playbackURL = try await preparePlaybackURL(for: song, remoteURL: audioURL)
                await startAudioEnginePlayback(song: song, from: playbackURL, startTime: resumeTime)
                await loadLyrics(for: song)
            } catch {
                errorMessage = "Playback resume failed: \(error.localizedDescription)"
            }
        }
    }

    private func configureAudioEngine() {
        audioEngine.attach(playerNode)
        audioEngine.attach(equalizerNode)
        audioEngine.connect(playerNode, to: equalizerNode, format: nil)
        audioEngine.connect(equalizerNode, to: audioEngine.mainMixerNode, format: nil)
        audioEffects.isAvailable = true
        audioEffects.bassBoostAvailable = true
        applyAudioEffects()
    }

    private func configureAudioSessionNotifications() {
        NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            MainActor.assumeIsolated {
                self?.handleAudioSessionInterruption(notification)
            }
        }

        NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            MainActor.assumeIsolated {
                self?.handleAudioRouteChange(notification)
            }
        }
    }

    private func handleAudioSessionInterruption(_ notification: Notification) {
        guard
            let userInfo = notification.userInfo,
            let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
            let type = AVAudioSession.InterruptionType(rawValue: typeValue)
        else { return }

        switch type {
        case .began:
            shouldResumeAfterInterruption = isPlaying
            pauseActivePlayback()
            isPlaying = false
            updateNowPlayingInfo()
            persistPlaybackState()
        case .ended:
            guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else {
                shouldResumeAfterInterruption = false
                return
            }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if shouldResumeAfterInterruption && options.contains(.shouldResume) {
                shouldResumeAfterInterruption = false
                resumeCurrentPlayback()
            } else {
                shouldResumeAfterInterruption = false
            }
        @unknown default:
            break
        }
    }

    private func handleAudioRouteChange(_ notification: Notification) {
        guard
            let userInfo = notification.userInfo,
            let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
            let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue)
        else { return }

        if reason == .oldDeviceUnavailable, isPlaying {
            pauseActivePlayback()
            isPlaying = false
            updateNowPlayingInfo()
            persistPlaybackState()
        }
    }

    private func applyAudioEffectsToEngine() {
        let bands = equalizerNode.bands
        for (index, bandState) in audioEffects.bands.enumerated() where index < bands.count - 1 {
            let band = bands[index]
            band.filterType = .parametric
            band.frequency = Float(bandState.centerFrequency) / 1000
            band.bandwidth = 1
            band.gain = audioEffects.isEnabled ? Float(bandState.currentLevel) / 100 : 0
            band.bypass = !audioEffects.isEnabled
        }

        if let bassBand = bands.last {
            bassBand.filterType = .lowShelf
            bassBand.frequency = 120
            bassBand.bandwidth = 0.7
            bassBand.gain = audioEffects.bassBoostEnabled ? Float(audioEffects.bassBoostStrength) / 100 : 0
            bassBand.bypass = !audioEffects.bassBoostEnabled
        }

        equalizerNode.globalGain = 0
    }

    private func applyAudioEffects() {
        applyAudioEffectsToEngine()
        radioAudioProcessor.update(effects: audioEffects)
    }

    private func pauseActivePlayback() {
        switch playbackBackend {
        case .audioEngine:
            pausedEngineTime = currentAudioEngineTime()
            playerNode.pause()
        case .avPlayer:
            player.pause()
        case .none:
            break
        }
    }

    private func toggleAudioEnginePlayback() {
        if isPlaying {
            pausedEngineTime = currentAudioEngineTime()
            playerNode.pause()
        } else {
            guard let currentSong, let playbackURL = currentPlaybackURL else {
                activateAudioSession()
                if !audioEngine.isRunning {
                    try? audioEngine.start()
                }
                playerNode.play()
                isPlaying.toggle()
                return
            }

            let resumeTime = pausedEngineTime
            Task {
                await startAudioEnginePlayback(song: currentSong, from: playbackURL, startTime: resumeTime)
            }
            return
        }
        isPlaying.toggle()
    }

    private func stopEnginePlayback(resetState: Bool) {
        audioEnginePlaybackToken = nil
        playerNode.stop()
        currentAudioFile = nil
        currentPlaybackURL = nil
        engineScheduledStartTime = 0
        pausedEngineTime = 0
        if resetState {
            playbackBackend = .none
        }
    }

    private func currentAudioEngineTime() -> Double {
        if !isPlaying {
            return pausedEngineTime
        }
        guard
            let lastRenderTime = playerNode.lastRenderTime,
            let playerTime = playerNode.playerTime(forNodeTime: lastRenderTime)
        else {
            return pausedEngineTime
        }
        return engineScheduledStartTime + Double(playerTime.sampleTime) / playerTime.sampleRate
    }

    private func seekAudioEngine(to seconds: Double) {
        guard let currentSong, let playbackURL = currentPlaybackURL else { return }
        Task {
            await startAudioEnginePlayback(song: currentSong, from: playbackURL, startTime: seconds)
        }
    }

    private func playAudioEngineSong(_ song: Song) async {
        guard let audioURL = song.audioURL else { return }
        do {
            let playbackURL = try await preparePlaybackURL(for: song, remoteURL: audioURL)
            await startAudioEnginePlayback(song: song, from: playbackURL, startTime: 0)
        } catch {
            errorMessage = "Playback failed: \(error.localizedDescription)"
        }
    }

    private func preparePlaybackURL(for song: Song, remoteURL: URL) async throws -> URL {
        if remoteURL.isFileURL {
            return remoteURL
        }
        if let downloaded = downloadedSongs.first(where: { $0.id == song.id }) {
            let localURL = URL(fileURLWithPath: downloaded.localAudioPath)
            if FileManager.default.fileExists(atPath: localURL.path) {
                return localURL
            }
        }
        return try await storage.preparePlaybackFile(from: remoteURL, cacheKey: song.id)
    }

    private func startAudioEnginePlayback(song: Song, from url: URL, startTime: Double) async {
        do {
            activateAudioSession()
            player.pause()
            isRadioPlaying = false

            let audioFile = try AVAudioFile(forReading: url)
            currentAudioFile = audioFile
            currentPlaybackURL = url
            duration = Double(audioFile.length) / audioFile.processingFormat.sampleRate
            engineScheduledStartTime = startTime
            pausedEngineTime = startTime

            audioEngine.disconnectNodeOutput(playerNode)
            audioEngine.disconnectNodeOutput(equalizerNode)
            audioEngine.connect(playerNode, to: equalizerNode, format: audioFile.processingFormat)
            audioEngine.connect(equalizerNode, to: audioEngine.mainMixerNode, format: audioFile.processingFormat)
            applyAudioEffects()

            if !audioEngine.isRunning {
                try audioEngine.start()
            }

            playerNode.stop()
            let playbackToken = UUID()
            audioEnginePlaybackToken = playbackToken
            let sampleRate = audioFile.processingFormat.sampleRate
            let startingFrame = AVAudioFramePosition(startTime * sampleRate)
            let availableFrames = max(audioFile.length - startingFrame, 0)
            playerNode.scheduleSegment(
                audioFile,
                startingFrame: startingFrame,
                frameCount: AVAudioFrameCount(availableFrames),
                at: nil,
                completionCallbackType: .dataPlayedBack
            ) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard
                        let self,
                        self.playbackBackend == .audioEngine,
                        self.audioEnginePlaybackToken == playbackToken
                    else { return }
                    self.handlePlaybackEnded()
                }
            }

            currentSong = song
            playbackBackend = .audioEngine
            playerNode.play()
            isPlaying = true
            playingSoundbiteID = song.id.hasPrefix("soundbite_") ? song.id.replacingOccurrences(of: "soundbite_", with: "") : nil
            updateNowPlayingInfo()
            persistPlaybackState()
        } catch {
            playbackBackend = .none
            errorMessage = "Playback failed: \(error.localizedDescription)"
        }
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        } catch {
            NSLog("Audio session category setup failed: \(error.localizedDescription)")
        }
    }

    private func configureAVPlayer() {
        player.automaticallyWaitsToMinimizeStalling = true
        player.allowsExternalPlayback = true
        player.audiovisualBackgroundPlaybackPolicy = .automatic
        observeTimeControlStatus()
    }

    private func configureRadioAudioMix(for item: AVPlayerItem) async {
        do {
            let tracks = try await item.asset.loadTracks(withMediaType: .audio)
            guard player.currentItem === item || player.currentItem == nil else { return }
            guard let track = tracks.first, let tap = makeRadioProcessingTap(processor: radioAudioProcessor) else { return }

            let inputParameters = AVMutableAudioMixInputParameters(track: track)
            inputParameters.audioTapProcessor = tap

            let mix = AVMutableAudioMix()
            mix.inputParameters = [inputParameters]
            item.audioMix = mix
            radioAudioProcessor.update(effects: audioEffects)
        } catch {
            errorMessage = "Radio effects unavailable: \(error.localizedDescription)"
        }
    }

    private func activateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            errorMessage = "Audio activation failed: \(error.localizedDescription)"
        }
    }

    private func observePlayerItem(_ item: AVPlayerItem) {
        playerItemStatusObservation?.invalidate()
        playerItemStatusObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                self?.handlePlayerItemStatusChange(item.status, item: item)
            }
        }
    }

    private func observeTimeControlStatus() {
        playerTimeControlStatusObservation?.invalidate()
        playerTimeControlStatusObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            Task { @MainActor [weak self] in
                self?.handlePlayerTimeControlStatusChange(player.timeControlStatus)
            }
        }
    }

    private func handlePlayerItemStatusChange(_ status: AVPlayerItem.Status, item: AVPlayerItem) {
        guard playbackBackend == .avPlayer else { return }

        switch status {
        case .readyToPlay:
            errorMessage = nil
            radioRetryAttempts = 0
        case .failed:
            if isRadioPlaying {
                errorMessage = item.error?.localizedDescription ?? "Radio playback failed."
                retryRadioPlaybackAfterFailure()
            }
        case .unknown:
            break
        @unknown default:
            break
        }
    }

    private func handlePlayerTimeControlStatusChange(_ status: AVPlayer.TimeControlStatus) {
        guard playbackBackend == .avPlayer else { return }

        switch status {
        case .paused:
            if isRadioPlaying {
                isPlaying = false
            }
        case .waitingToPlayAtSpecifiedRate:
            if isRadioPlaying {
                isPlaying = false
            }
        case .playing:
            if isRadioPlaying {
                isPlaying = true
                errorMessage = nil
            }
        @unknown default:
            break
        }

        updateNowPlayingInfo()
        persistPlaybackState()
    }

    private func retryRadioPlaybackAfterFailure() {
        guard isRadioPlaying else { return }

        radioRetryAttempts += 1
        let attempt = radioRetryAttempts
        let delay = min(Double(attempt), 5)

        Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            await MainActor.run {
                guard let self, self.isRadioPlaying, self.radioRetryAttempts == attempt else { return }
                Task {
                    await self.refreshRadioState()
                    await MainActor.run {
                        self.playRadio()
                    }
                }
            }
        }
    }

    private func syncRadioNowPlaying(with state: RadioState) {
        guard isRadioPlaying else { return }

        let resolvedSong = state.current?.song ?? Song(
            id: "radio_live",
            title: "Neuro Karaoke Radio",
            artist: "Live Stream",
            coverURL: nil,
            audioURL: URL(string: NeuroKaraokeAPI.radioStreamURL),
            singer: .neuro,
            playlistName: "Radio",
            artCredit: nil
        )

        if currentSong != resolvedSong {
            currentSong = resolvedSong
        }
        updateNowPlayingInfo()
        persistPlaybackState()
    }

    private func configureRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.removeTarget(nil)
        commandCenter.pauseCommand.removeTarget(nil)
        commandCenter.nextTrackCommand.removeTarget(nil)
        commandCenter.previousTrackCommand.removeTarget(nil)
        commandCenter.changePlaybackPositionCommand.removeTarget(nil)

        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resumeFromRemoteCommand() }
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pauseFromRemoteCommand() }
            return .success
        }
        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.playNext() }
            return .success
        }
        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.playPrevious() }
            return .success
        }
        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in
                guard let self, self.duration > 0 else { return }
                self.seek(to: event.positionTime / self.duration)
            }
            return .success
        }
    }

    private func resumeFromRemoteCommand() {
        if currentSong == nil, let state = storage.loadPlaybackState(), let restoredSong = state.currentSong {
            queue = state.queue
            currentIndex = min(state.currentIndex, max(state.queue.count - 1, 0))
            currentSong = restoredSong
            currentTime = state.currentTime
            duration = state.duration
            isRadioPlaying = state.isRadioPlaying
            resumeCurrentPlayback()
            return
        }
        if playbackBackend == .none {
            resumeCurrentPlayback()
            return
        }
        if !isPlaying {
            togglePlayback()
        }
    }

    private func pauseFromRemoteCommand() {
        if isPlaying {
            togglePlayback()
        }
    }

    private func updateNowPlayingInfo() {
        guard let currentSong else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            lastNowPlayingArtworkURL = nil
            cachedNowPlayingArtwork = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: currentSong.title,
            MPMediaItemPropertyArtist: currentSong.artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]

        if let artwork = cachedNowPlayingArtwork, currentSong.coverURL == lastNowPlayingArtworkURL {
            info[MPMediaItemPropertyArtwork] = artwork
        } else if let artworkURL = currentSong.coverURL {
            lastNowPlayingArtworkURL = artworkURL
            Task.detached { [artworkURL] in
                guard let data = try? Data(contentsOf: artworkURL), let image = UIImage(data: data) else { return }
                let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                await MainActor.run {
                    guard self.lastNowPlayingArtworkURL == artworkURL else { return }
                    self.cachedNowPlayingArtwork = artwork
                    var updated = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? info
                    updated[MPMediaItemPropertyArtwork] = artwork
                    MPNowPlayingInfoCenter.default().nowPlayingInfo = updated
                }
            }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func persistPlaybackState() {
        let state = PlaybackState(
            currentSong: currentSong,
            queue: queue,
            currentIndex: currentIndex,
            currentTime: currentTime,
            duration: duration,
            isRadioPlaying: isRadioPlaying,
            queueSource: currentQueueSource
        )
        storage.savePlaybackState(state)
        storage.savePlaybackPreferences(repeatMode: repeatMode, shuffleEnabled: isShuffleEnabled)
    }

    private func restorePlaybackState() {
        guard let state = storage.loadPlaybackState(), let restoredSong = state.currentSong else { return }
        let preferences = storage.loadPlaybackPreferences()
        repeatMode = preferences.repeatMode
        isShuffleEnabled = preferences.shuffleEnabled
        queue = state.queue
        currentIndex = min(state.currentIndex, max(state.queue.count - 1, 0))
        currentSong = restoredSong
        currentTime = state.currentTime
        duration = state.duration
        isRadioPlaying = state.isRadioPlaying
        currentQueueSource = state.queueSource
        currentPlaybackURL = restoredSong.audioURL
        pausedEngineTime = state.currentTime
        isPlaying = false
        updateNowPlayingInfo()
    }

    func loadLyrics(for song: Song) async {
        guard !song.title.isEmpty, !song.artist.isEmpty else {
            lyricLines = []
            return
        }
        isLoadingLyrics = true
        defer { isLoadingLyrics = false }

        if let cached = storage.loadLyrics(title: song.title, artist: song.artist) {
            lyricLines = cached
            return
        }

        do {
            let result = try await api.fetchLyrics(trackName: song.title, artistName: song.artist)
            lyricLines = result
            storage.saveLyrics(result, title: song.title, artist: song.artist)
        } catch {
            lyricLines = []
        }
    }

    private static func generateCodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 64)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func generateCodeChallenge(_ verifier: String) -> String {
        let data = Data(verifier.utf8)
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { bytes in
            _ = CC_SHA256(bytes.baseAddress, CC_LONG(data.count), &digest)
        }
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private func updateProgress(_ fraction: Double, _ status: String, _ detail: String) {
        setupProgress = SetupProgress(fraction: fraction, status: status, detail: detail)
    }

    static let defaultEqualizerBands: [EqualizerBand] = [
        EqualizerBand(index: 0, centerFrequency: 60_000, minLevel: -1200, maxLevel: 1200, currentLevel: 0),
        EqualizerBand(index: 1, centerFrequency: 230_000, minLevel: -1200, maxLevel: 1200, currentLevel: 0),
        EqualizerBand(index: 2, centerFrequency: 910_000, minLevel: -1200, maxLevel: 1200, currentLevel: 0),
        EqualizerBand(index: 3, centerFrequency: 3_600_000, minLevel: -1200, maxLevel: 1200, currentLevel: 0),
        EqualizerBand(index: 4, centerFrequency: 14_000_000, minLevel: -1200, maxLevel: 1200, currentLevel: 0)
    ]

    static let defaultEqualizerPresets: [EqualizerPreset] = [
        EqualizerPreset(index: 0, name: "Pop", bandLevels: [200, 100, 0, 200, 300]),
        EqualizerPreset(index: 1, name: "Rock", bandLevels: [300, 150, -100, 150, 300]),
        EqualizerPreset(index: 2, name: "Bass Boost", bandLevels: [500, 320, 80, -80, -120]),
        EqualizerPreset(index: 3, name: "Treble Boost", bandLevels: [-150, -80, 0, 280, 500]),
        EqualizerPreset(index: 4, name: "Vocal", bandLevels: [-120, 120, 300, 220, 40])
    ]
}

enum AppTab: Hashable {
    case home
    case search
    case explore
    case library
    case radio
    case soundbites
    case setlists
    case artists
}

extension AppModel {
    static func previewModel(selectedTab: AppTab = .home, includeCurrentSong: Bool = true) -> AppModel {
        let model = AppModel()

        let song1 = Song(
            id: "preview-song-1",
            title: "Artificial Aria",
            artist: "Neuro-sama",
            coverURL: nil,
            audioURL: nil,
            singer: .neuro,
            playlistName: "Launch Sequence",
            artCredit: nil
        )
        let song2 = Song(
            id: "preview-song-2",
            title: "Hex Appeal",
            artist: "Evil Neuro",
            coverURL: nil,
            audioURL: nil,
            singer: .evil,
            playlistName: "Launch Sequence",
            artCredit: nil
        )
        let song3 = Song(
            id: "preview-song-3",
            title: "Double Take",
            artist: "Neuro-sama & Evil Neuro",
            coverURL: nil,
            audioURL: nil,
            singer: .duet,
            playlistName: "Community Favorites",
            artCredit: nil
        )

        let setlist = Playlist(
            id: "preview-playlist-1",
            title: "Launch Sequence",
            description: "A sample setlist used for SwiftUI previews.",
            coverURL: nil,
            previewCoverURLs: [],
            songCount: 2,
            songs: [song1, song2]
        )
        let publicPlaylist = Playlist(
            id: "preview-playlist-2",
            title: "Community Favorites",
            description: "A public playlist preview.",
            coverURL: nil,
            previewCoverURLs: [],
            songCount: 1,
            songs: [song3]
        )
        let userPlaylist = Playlist(
            id: "preview-user-playlist",
            title: "Offline Mix",
            description: "Downloaded songs for travel.",
            coverURL: nil,
            previewCoverURLs: [],
            songCount: 2,
            songs: [song1, song3],
            isUserPlaylist: true
        )

        model.isReady = true
        model.selectedTab = selectedTab
        model.playlists = [setlist]
        model.publicPlaylists = [publicPlaylist]
        model.userPlaylists = [userPlaylist]
        model.allSongs = [song1, song2, song3]
        model.trendingSongs = [song3, song1, song2]
        model.favoriteSongs = [song1, song3]
        model.downloadedSongs = [
            DownloadedSong(
                id: song1.id,
                title: song1.title,
                artist: song1.artist,
                coverURL: nil,
                sourceAudioURL: nil,
                localAudioPath: "/tmp/\(song1.id).mp3",
                localCoverPath: nil,
                singer: song1.singer,
                artCredit: nil,
                downloadedAt: .now,
                fileSize: 3_145_728
            )
        ]
        model.downloadProgress = [song2.id: 0.42]
        model.artists = [
            Artist(id: "preview-artist-1", name: "Neuro-sama", imageURL: nil, songCount: 12, summary: "Preview artist summary."),
            Artist(id: "preview-artist-2", name: "Evil Neuro", imageURL: nil, songCount: 8, summary: "Preview artist summary.")
        ]
        model.coverDistribution = CoverDistribution(totalSongs: 20, neuroCount: 11, evilCount: 5, duetCount: 3, otherCount: 1)
        model.currentUser = User(
            id: "preview-user",
            username: "preview",
            discriminator: "0001",
            avatar: nil,
            accessToken: nil,
            apiToken: nil
        )
        model.radioState = RadioState(
            current: RadioSong(
                id: "preview-radio-current",
                title: "Live Signal",
                originalArtists: ["Neuro-sama"],
                coverArtists: ["Neuro-sama"],
                duration: 215,
                coverArt: nil
            ),
            upcoming: [
                RadioSong(id: "preview-radio-next", title: "Packet Loss", originalArtists: ["Evil Neuro"], coverArtists: ["Evil Neuro"], duration: 190, coverArt: nil)
            ],
            history: [
                RadioSong(id: "preview-radio-history", title: "Cold Start", originalArtists: ["Neuro-sama"], coverArtists: ["Neuro-sama"], duration: 205, coverArt: nil)
            ],
            listenerCount: 128,
            offline: false
        )
        model.audioEffects = AudioEffectsState(
            isEnabled: true,
            bands: Self.defaultEqualizerBands,
            presets: Self.defaultEqualizerPresets,
            currentPresetIndex: 0,
            isAvailable: true,
            bassBoostEnabled: true,
            bassBoostStrength: 320,
            bassBoostAvailable: true
        )

        if includeCurrentSong {
            model.currentSong = song1
            model.isPlaying = true
            model.duration = 215
            model.currentTime = 72
            model.queue = [song1, song2, song3]
            model.currentIndex = 0
            model.currentQueueSource = .playlist(setlist.id)
            model.lyricLines = [
                LyricLine(timestamp: 0, text: "Booting up the harmony"),
                LyricLine(timestamp: 30, text: "Electric voices in the dark"),
                LyricLine(timestamp: 60, text: "Signal stable, sing the line")
            ]
        }

        return model
    }
}

private struct SeedCatalog: Decodable {
    let playlists: [SeedPlaylist]
}

private struct SeedPlaylist: Decodable {
    let id: String
    let name: String?
    let description: String?
    let coverUrl: String?
    let previewCovers: [String]?
    let songCount: Int?
}

private struct PlaylistResponse: Decodable {
    let name: String?
    let description: String?
    let cover: String?
    let songs: [PlaylistSongResponse]
}

private struct PlaylistSongResponse: Decodable {
    let title: String
    let originalArtists: String?
    let coverArtists: String?
    let coverArt: String?
    let audioUrl: String?
    let artCredit: String?
}

private struct ArtistResponse: Decodable {
    let id: String
    let name: String
    let imagePath: String?
    let songCount: Int?
    let summary: String?
}

private struct CoverDistributionResponse: Decodable {
    let totalSongs: Int
    let neuroCount: Int
    let evilCount: Int
    let duetCount: Int
    let otherCount: Int
}

private struct TrendingSongResponse: Decodable {
    struct CoverArtResponse: Decodable {
        let cloudflareId: String?
        let absolutePath: String?
    }

    let id: String?
    let title: String
    let originalArtists: [String]?
    let coverArtists: [String]?
    let coverArt: CoverArtResponse?
    let absolutePath: String?
}

private struct RadioStateResponse: Decodable {
    let current: RadioSong?
    let upcoming: [RadioSong]
    let history: [RadioSong]
    let listenerCount: Int
    let offline: Bool
}

private struct DiscordTokenResponse: Decodable {
    let access_token: String
}

private struct DiscordUserResponse: Decodable {
    let id: String
    let username: String
    let global_name: String?
    let discriminator: String?
    let avatar: String?
}

private struct LyricsResponse: Decodable {
    let syncedLyrics: String?
    let plainLyrics: String?
}

struct PlaybackPreferences: Codable {
    let repeatMode: RepeatMode
    let shuffleEnabled: Bool
}

private struct SoundbiteListResponse: Decodable {
    let items: [SoundbiteResponseItem]
    let totalCount: Int
    let page: Int
    let pageSize: Int
}

private struct SoundbiteResponseItem: Decodable {
    let id: String
    let title: String
    let comments: String?
    let duration: Int
    let absolutePath: String?
    let tag: Int
    let audioUrl: String?
    let uploadedAt: String?
    let uploadedBy: String?
    let imageUrl: String?
    let embeddable: Bool
    let playCount: Int
}

private struct ApiPublicPlaylist: Decodable {
    let id: String
    let name: String
    let description: String?
    let coverUrl: String?
    let mosaicCovers: [String]
    let songCount: Int
    let createdBy: String?
}

struct PlaylistInfo {
    let title: String
    let description: String
    let coverURL: URL?
    let previewCoverURLs: [URL]
    let songCount: Int
}

struct NeuroKaraokeAPI {
    static let radioStreamURL = "https://radio.twinskaraoke.com/listen/neuro_21/radio.mp3"
    private let discordClientID = "1447802634621943850"
    private let discordRedirectURI = "neurokaraoke://auth"
    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 40
        return URLSession(configuration: configuration)
    }()

    private let decoder = JSONDecoder()
    private let baseURL = URL(string: "https://idk.neurokaraoke.com")!
    private let apiURL = URL(string: "https://api.neurokaraoke.com")!

    func loadSeedPlaylists() throws -> [Playlist] {
        guard let url = Bundle.main.url(forResource: "playlists", withExtension: "json") else {
            throw URLError(.fileDoesNotExist)
        }

        let data = try Data(contentsOf: url)
        let catalog = try decoder.decode(SeedCatalog.self, from: data)

        return catalog.playlists.map { seed in
            Playlist(
                id: seed.id,
                title: seed.name ?? "",
                description: seed.description ?? "",
                coverURL: URL(string: seed.coverUrl ?? ""),
                previewCoverURLs: (seed.previewCovers ?? []).compactMap(URL.init(string:)),
                songCount: seed.songCount ?? 0,
                songs: []
            )
        }
    }

    func fetchPlaylist(id: String, fallback: Playlist) async throws -> Playlist {
        let response: PlaylistResponse = try await fetchJSON(from: endpointURL(base: baseURL, path: "public/playlist/\(id)"))
        let songs = response.songs.enumerated().compactMap { offset, song in
            let audioURL = URL(string: song.audioUrl ?? "")
            let songID = audioURL?.absoluteString.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "\(id)-\(offset)"
            let artists = (song.originalArtists?.isEmpty == false ? song.originalArtists : nil) ?? "Unknown Artist"

            return Song(
                id: songID,
                title: song.title,
                artist: artists,
                coverURL: absoluteMediaURL(path: song.coverArt) ?? song.audioUrl.flatMap(derivedCoverURL(audioPath:)),
                audioURL: audioURL,
                singer: singer(from: song.coverArtists),
                playlistName: response.name,
                artCredit: song.artCredit
            )
        }

        let previewCoverURLs = response.songs.prefix(20).compactMap { song -> URL? in
            if let explicit = absoluteMediaURL(path: song.coverArt) {
                return explicit
            }
            return song.audioUrl.flatMap(derivedCoverURL(audioPath:))
        }

        return Playlist(
            id: id,
            title: response.name ?? fallback.title,
            description: response.description ?? fallback.description,
            coverURL: absoluteMediaURL(path: response.cover) ?? fallback.coverURL,
            previewCoverURLs: (Array(NSOrderedSet(array: previewCoverURLs)) as? [URL] ?? previewCoverURLs).isEmpty ? fallback.previewCoverURLs : (Array(NSOrderedSet(array: previewCoverURLs)) as? [URL] ?? previewCoverURLs),
            songCount: songs.count,
            songs: songs
        )
    }

    func fetchPlaylistInfo(id: String) async throws -> PlaylistInfo {
        let response: PlaylistResponse = try await fetchJSON(from: endpointURL(base: baseURL, path: "public/playlist/\(id)"))
        let previewCoverURLs = response.songs.prefix(20).compactMap { song -> URL? in
            if let explicit = absoluteMediaURL(path: song.coverArt) {
                return explicit
            }
            return song.audioUrl.flatMap(derivedCoverURL(audioPath:))
        }

        return PlaylistInfo(
            title: response.name ?? "",
            description: response.description ?? "",
            coverURL: absoluteMediaURL(path: response.cover),
            previewCoverURLs: Array(NSOrderedSet(array: previewCoverURLs)) as? [URL] ?? previewCoverURLs,
            songCount: response.songs.count
        )
    }

    func fetchPlaylistSongs(id: String) async throws -> [Song] {
        let response: PlaylistResponse = try await fetchJSON(from: endpointURL(base: baseURL, path: "public/playlist/\(id)"))
        return response.songs.enumerated().compactMap { offset, song in
            let audioURL = URL(string: song.audioUrl ?? "")
            let songID = audioURL?.absoluteString.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "\(id)-\(offset)"
            let artists = (song.originalArtists?.isEmpty == false ? song.originalArtists : nil) ?? "Unknown Artist"

            return Song(
                id: songID,
                title: song.title,
                artist: artists,
                coverURL: absoluteMediaURL(path: song.coverArt) ?? song.audioUrl.flatMap(derivedCoverURL(audioPath:)),
                audioURL: audioURL,
                singer: singer(from: song.coverArtists),
                playlistName: response.name,
                artCredit: song.artCredit
            )
        }
    }

    func fetchArtists() async throws -> [Artist] {
        let response: [ArtistResponse] = try await fetchJSON(from: endpointURL(base: apiURL, path: "api/artists"))
        return response.map { artist in
            Artist(
                id: artist.id,
                name: artist.name,
                imageURL: absoluteMediaURL(path: artist.imagePath),
                songCount: artist.songCount ?? 0,
                summary: artist.summary ?? ""
            )
        }
        .sorted { $0.songCount > $1.songCount }
    }

    func fetchCoverDistribution() async throws -> CoverDistribution {
        let response: CoverDistributionResponse = try await fetchJSON(from: endpointURL(base: apiURL, path: "api/stats/cover-distribution"))
        return CoverDistribution(
            totalSongs: response.totalSongs,
            neuroCount: response.neuroCount,
            evilCount: response.evilCount,
            duetCount: response.duetCount,
            otherCount: response.otherCount
        )
    }

    func fetchTrendingSongs() async throws -> [Song] {
        let url = endpointURL(base: apiURL, path: "api/explore/trendings").appending(queryItems: [
            URLQueryItem(name: "days", value: "7")
        ])
        let response: [TrendingSongResponse] = try await fetchJSON(from: url)
        return response.map { item in
            Song(
                id: item.id ?? item.title,
                title: item.title,
                artist: nonEmpty(item.originalArtists?.joined(separator: ", ")) ?? "Unknown Artist",
                coverURL: trendingCoverURL(item.coverArt),
                audioURL: item.absolutePath.flatMap { URL(string: "https://storage.neurokaraoke.com/\($0)") },
                singer: singer(from: item.coverArtists?.joined(separator: ", ")),
                playlistName: "Trending",
                artCredit: nil
            )
        }
    }

    func fetchRadioState() async throws -> RadioState {
        let socketURL = URL(string: "https://socket.neurokaraoke.com")!
        let response: RadioStateResponse = try await fetchJSON(from: endpointURL(base: socketURL, path: "api/radio/current-state"))
        return RadioState(
            current: response.current,
            upcoming: response.upcoming,
            history: response.history,
            listenerCount: response.listenerCount,
            offline: response.offline
        )
    }

    func exchangeDiscordCode(code: String, verifier: String) async throws -> User {
        var request = URLRequest(url: URL(string: "https://discord.com/api/oauth2/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = [
            "client_id=\(discordClientID)",
            "grant_type=authorization_code",
            "code=\(code)",
            "redirect_uri=\(discordRedirectURI.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? discordRedirectURI)",
            "code_verifier=\(verifier)"
        ].joined(separator: "&")
        request.httpBody = body.data(using: .utf8)

        let (tokenData, tokenResponse) = try await session.data(for: request)
        guard let tokenHTTP = tokenResponse as? HTTPURLResponse, 200..<300 ~= tokenHTTP.statusCode else {
            throw URLError(.userAuthenticationRequired)
        }
        let discordToken = try decoder.decode(DiscordTokenResponse.self, from: tokenData)

        var exchangeRequest = URLRequest(url: endpointURL(base: baseURL, path: "api/auth/discord-token"))
        exchangeRequest.httpMethod = "POST"
        exchangeRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        exchangeRequest.httpBody = try JSONSerialization.data(withJSONObject: ["accessToken": discordToken.access_token])

        let (exchangeData, exchangeResponse) = try await session.data(for: exchangeRequest)
        let apiToken: String?
        if let exchangeHTTP = exchangeResponse as? HTTPURLResponse, 200..<300 ~= exchangeHTTP.statusCode {
            let responseString = String(data: exchangeData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if responseString.hasPrefix("{"),
               let object = try JSONSerialization.jsonObject(with: exchangeData) as? [String: Any] {
                apiToken = object["token"] as? String ?? object["accessToken"] as? String
            } else {
                apiToken = responseString.replacingOccurrences(of: "\"", with: "")
            }
        } else {
            apiToken = nil
        }

        var userRequest = URLRequest(url: URL(string: "https://discord.com/api/users/@me")!)
        userRequest.setValue("Bearer \(discordToken.access_token)", forHTTPHeaderField: "Authorization")
        let (userData, userResponse) = try await session.data(for: userRequest)
        guard let userHTTP = userResponse as? HTTPURLResponse, 200..<300 ~= userHTTP.statusCode else {
            throw URLError(.userAuthenticationRequired)
        }
        let discordUser = try decoder.decode(DiscordUserResponse.self, from: userData)

        return User(
            id: discordUser.id,
            username: discordUser.global_name ?? discordUser.username,
            discriminator: discordUser.discriminator ?? "0",
            avatar: discordUser.avatar,
            accessToken: discordToken.access_token,
            apiToken: apiToken
        )
    }

    func fetchFavorites(accessToken: String) async throws -> [Song] {
        var request = URLRequest(url: endpointURL(base: apiURL, path: "api/favorites/type").appending(queryItems: [URLQueryItem(name: "type", value: "0")]))
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
        let raw = try JSONSerialization.jsonObject(with: data)
        let items = (raw as? [[String: Any]]) ?? []
        return items.compactMap { item in
            parseServerSong(item["song"] as? [String: Any] ?? item)
        }
    }

    func fetchUserPlaylists(accessToken: String) async throws -> [Playlist] {
        var request = URLRequest(url: endpointURL(base: apiURL, path: "api/user/playlists"))
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
        let raw = try JSONSerialization.jsonObject(with: data)
        let items = (raw as? [[String: Any]]) ?? []
        return items.compactMap(parseServerPlaylist)
    }

    func fetchPublicPlaylists() async throws -> [Playlist] {
        let response: [Playlist] = try await {
            let items: [ApiPublicPlaylist] = try await fetchPublicPlaylistItems()
            return items.map { item in
                Playlist(
                    id: item.id,
                    title: item.name,
                    description: item.createdBy.map { "by \($0)" } ?? (item.description ?? ""),
                    coverURL: URL(string: item.coverUrl ?? "") ?? item.mosaicCovers.first.flatMap(URL.init(string:)),
                    previewCoverURLs: item.mosaicCovers.compactMap(URL.init(string:)),
                    songCount: item.songCount,
                    songs: [],
                    isUserPlaylist: false
                )
            }
        }()
        return response
    }

    func fetchLyrics(trackName: String, artistName: String) async throws -> [LyricLine] {
        let queryURL = URL(string: "https://lrclib.net/api/get")!.appending(queryItems: [
            URLQueryItem(name: "track_name", value: trackName),
            URLQueryItem(name: "artist_name", value: artistName)
        ])
        var request = URLRequest(url: queryURL)
        request.setValue("NeuroKaraoke iOS", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await session.data(for: request)
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 404 {
                return try await searchLyricsFallback(query: "\(trackName) \(artistName)")
            }
            guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
                throw URLError(.badServerResponse)
            }
            let lyrics = try decoder.decode(LyricsResponse.self, from: data)
            return parseLyricsResponse(lyrics)
        } catch {
            return try await searchLyricsFallback(query: "\(trackName) \(artistName)")
        }
    }

    func fetchSoundbites(page: Int = 1, pageSize: Int = 30, search: String? = nil) async throws -> (items: [Soundbite], totalCount: Int, page: Int) {
        var url = endpointURL(base: apiURL, path: "api/soundbites").appending(queryItems: [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(pageSize))
        ])
        if let search, !search.isEmpty {
            url = url.appending(queryItems: [URLQueryItem(name: "search", value: search)])
        }

        let response: SoundbiteListResponse = try await fetchJSON(from: url)
        let items = response.items.map { item in
            Soundbite(
                id: item.id,
                title: item.title,
                comments: item.comments,
                duration: item.duration,
                absolutePath: item.absolutePath,
                tag: item.tag,
                audioURL: URL(string: item.audioUrl ?? "") ?? absoluteMediaURL(path: item.absolutePath),
                uploadedAt: item.uploadedAt,
                uploadedBy: item.uploadedBy,
                imageURL: URL(string: item.imageUrl ?? "") ?? absoluteMediaURL(path: item.absolutePath),
                embeddable: item.embeddable,
                playCount: item.playCount
            )
        }
        return (items, response.totalCount, response.page)
    }

    private func fetchJSON<T: Decodable>(from url: URL) async throws -> T {
        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
        return try decoder.decode(T.self, from: data)
    }

    private func endpointURL(base: URL, path: String) -> URL {
        base.appending(path: path)
    }

    private func absoluteMediaURL(path: String?) -> URL? {
        guard let path, !path.isEmpty else { return nil }
        if path.hasPrefix("http"), let url = URL(string: path) {
            return url
        }
        if path.hasPrefix("/") {
            return URL(string: "https://storage.neurokaraoke.com\(path)")
        }
        return URL(string: "https://storage.neurokaraoke.com/\(path)")
    }

    private func derivedCoverURL(audioPath: String) -> URL? {
        let derived = audioPath
            .replacingOccurrences(of: "/audio/", with: "/images/")
            .replacingOccurrences(of: ".mp3", with: ".jpg")
        return URL(string: derived)
    }

    private func trendingCoverURL(_ coverArt: TrendingSongResponse.CoverArtResponse?) -> URL? {
        guard let coverArt else { return nil }
        if let cloudflareID = coverArt.cloudflareId, !cloudflareID.isEmpty {
            return URL(string: "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/\(cloudflareID)/public")
        }
        if let absolutePath = coverArt.absolutePath, !absolutePath.isEmpty {
            return URL(string: absolutePath)
        }
        return nil
    }

    private func singer(from coverArtists: String?) -> Singer {
        let value = coverArtists?.localizedLowercase ?? ""
        if value.contains("evil") && value.contains("neuro") {
            return .duet
        }
        if value.contains("evil") {
            return .evil
        }
        if value.isEmpty {
            return .other
        }
        return .neuro
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    private func parseServerPlaylist(_ object: [String: Any]) -> Playlist? {
        guard let id = object["id"] as? String else { return nil }
        let title = object["name"] as? String ?? "Unknown Playlist"
        let description = object["description"] as? String ?? ""
        let coverURL = resolveMediaURL(object["media"] as? [String: Any])
        let mosaic = (object["mosaicMedia"] as? [[String: Any]] ?? []).prefix(4).compactMap(resolveMediaURL)
        let songs = (object["songListDTOs"] as? [[String: Any]] ?? []).compactMap(parseServerSong)
        let songCount = object["songCount"] as? Int ?? songs.count
        return Playlist(
            id: id,
            title: title,
            description: description,
            coverURL: coverURL,
            previewCoverURLs: mosaic,
            songCount: songCount,
            songs: songs,
            isUserPlaylist: true
        )
    }

    private func parseServerSong(_ object: [String: Any]) -> Song? {
        guard let title = object["title"] as? String, !title.isEmpty else { return nil }
        let id = object["id"] as? String ?? title
        let artist: String = {
            if let originalArtists = object["originalArtists"] as? [String], !originalArtists.isEmpty {
                return originalArtists.joined(separator: ", ")
            }
            if let originalArtists = object["originalArtists"] as? String, !originalArtists.isEmpty {
                return originalArtists
            }
            if let artist = object["artist"] as? String, !artist.isEmpty {
                return artist
            }
            return "Unknown Artist"
        }()
        let audioURL: URL? = {
            if let audioString = object["audioUrl"] as? String, let url = URL(string: audioString) {
                return url
            }
            if let path = object["absolutePath"] as? String {
                return URL(string: path.hasPrefix("http") ? path : "https://storage.neurokaraoke.com/\(path)")
            }
            return nil
        }()
        let coverURL: URL? = {
            if let string = object["coverUrl"] as? String, let url = URL(string: string) {
                return url
            }
            if let coverArt = object["coverArt"] as? [String: Any] {
                return resolveMediaURL(coverArt)
            }
            if let coverArt = object["coverArt"] as? String {
                return URL(string: coverArt)
            }
            if let audioURL {
                return derivedCoverURL(audioPath: audioURL.absoluteString)
            }
            return nil
        }()
        let resolvedSinger: Singer = {
            if let coverArtists = object["coverArtists"] as? [String] {
                return self.singer(from: coverArtists.joined(separator: ", "))
            }
            if let coverArtists = object["coverArtists"] as? String {
                return self.singer(from: coverArtists)
            }
            return .neuro
        }()

        return Song(
            id: id,
            title: title,
            artist: artist,
            coverURL: coverURL,
            audioURL: audioURL,
            singer: resolvedSinger,
            playlistName: nil,
            artCredit: nil
        )
    }

    private func resolveMediaURL(_ object: [String: Any]?) -> URL? {
        guard let object else { return nil }
        if let cloudflareId = object["cloudflareId"] as? String, !cloudflareId.isEmpty {
            return URL(string: "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/\(cloudflareId)/public")
        }
        if let absolutePath = object["absolutePath"] as? String, !absolutePath.isEmpty {
            if absolutePath.hasPrefix("http") {
                return URL(string: absolutePath)
            }
            return URL(string: "https://images.neurokaraoke.com\(absolutePath.hasPrefix("/") ? absolutePath : "/\(absolutePath)")")
        }
        return nil
    }

    private func searchLyricsFallback(query: String) async throws -> [LyricLine] {
        let searchURL = URL(string: "https://lrclib.net/api/search")!.appending(queryItems: [
            URLQueryItem(name: "q", value: query)
        ])
        var request = URLRequest(url: searchURL)
        request.setValue("NeuroKaraoke iOS", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
        let results = try decoder.decode([LyricsResponse].self, from: data)
        return parseLyricsResponse(results.first)
    }

    private func parseLyricsResponse(_ response: LyricsResponse?) -> [LyricLine] {
        if let syncedLyrics = response?.syncedLyrics, !syncedLyrics.isEmpty {
            return parseSyncedLyrics(syncedLyrics)
        }
        if let plainLyrics = response?.plainLyrics, !plainLyrics.isEmpty {
            return plainLyrics
                .split(separator: "\n")
                .enumerated()
                .map { LyricLine(timestamp: Double($0.offset * 3), text: String($0.element)) }
        }
        return []
    }

    private func fetchPublicPlaylistItems() async throws -> [ApiPublicPlaylist] {
        let url = endpointURL(base: apiURL, path: "api/playlists/public")
        let raw = try await fetchJSONArray(from: url)
        return raw.compactMap { object in
            guard let id = object["id"] as? String else { return nil }
            let media = object["media"] as? [String: Any]
            let coverUrl = resolveMediaURL(media)?.absoluteString
            let mosaic = (object["mosaicMedia"] as? [[String: Any]] ?? []).prefix(4).compactMap { resolveMediaURL($0)?.absoluteString }
            return ApiPublicPlaylist(
                id: id,
                name: object["name"] as? String ?? "Unknown Playlist",
                description: object["description"] as? String,
                coverUrl: coverUrl,
                mosaicCovers: mosaic,
                songCount: object["songCount"] as? Int ?? 0,
                createdBy: object["createdBy"] as? String
            )
        }
    }

    private func fetchJSONArray(from url: URL) async throws -> [[String: Any]] {
        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
        return (try JSONSerialization.jsonObject(with: data) as? [[String: Any]]) ?? []
    }

    private func parseSyncedLyrics(_ content: String) -> [LyricLine] {
        let regex = try? NSRegularExpression(pattern: #"\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)"#)
        let lines = content.split(separator: "\n")
        return lines.compactMap { line in
            let lineString = String(line)
            guard
                let regex,
                let match = regex.firstMatch(in: lineString, range: NSRange(location: 0, length: lineString.utf16.count)),
                let minutesRange = Range(match.range(at: 1), in: lineString),
                let secondsRange = Range(match.range(at: 2), in: lineString),
                let millisRange = Range(match.range(at: 3), in: lineString),
                let textRange = Range(match.range(at: 4), in: lineString)
            else { return nil }

            let minutes = Double(lineString[minutesRange]) ?? 0
            let seconds = Double(lineString[secondsRange]) ?? 0
            let millisecondsText = String(lineString[millisRange])
            let milliseconds = Double(millisecondsText) ?? 0
            let resolvedMilliseconds = millisecondsText.count == 2 ? milliseconds / 100 : milliseconds / 1000
            return LyricLine(
                timestamp: minutes * 60 + seconds + resolvedMilliseconds,
                text: String(lineString[textRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            )
        }
    }
}

private extension URL {
    func appending(queryItems: [URLQueryItem]) -> URL {
        guard var components = URLComponents(url: self, resolvingAgainstBaseURL: false) else {
            return self
        }
        components.queryItems = (components.queryItems ?? []) + queryItems
        return components.url ?? self
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

final class LocalLibraryStore {
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private var baseURL: URL {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let directory = root.appending(path: "NeuroKaraoke", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private var downloadsURL: URL {
        let directory = baseURL.appending(path: "Downloads", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private var coversURL: URL {
        let directory = downloadsURL.appending(path: "Covers", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private var favoritesURL: URL { baseURL.appending(path: "favorites.json") }
    private var playlistsURL: URL { baseURL.appending(path: "playlists.local.json") }
    private var downloadsMetadataURL: URL { baseURL.appending(path: "downloads.json") }
    private var userURL: URL { baseURL.appending(path: "user.json") }
    private var playbackStateURL: URL { baseURL.appending(path: "playback-state.json") }
    private var playbackPreferencesURL: URL { baseURL.appending(path: "playback-preferences.json") }
    private var catalogCacheURL: URL { baseURL.appending(path: "catalog-cache.json") }
    private var audioEffectsURL: URL { baseURL.appending(path: "audio-effects.json") }
    private var playbackCacheURL: URL {
        let directory = baseURL.appending(path: "PlaybackCache", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
    private var lyricsCacheURL: URL {
        let directory = baseURL.appending(path: "Lyrics", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    func loadFavorites() -> [Song] {
        load([Song].self, from: favoritesURL) ?? []
    }

    func saveFavorites(_ songs: [Song]) {
        save(songs, to: favoritesURL)
    }

    func loadPlaylists() -> [Playlist] {
        load([Playlist].self, from: playlistsURL) ?? []
    }

    func savePlaylists(_ playlists: [Playlist]) {
        save(playlists, to: playlistsURL)
    }

    func loadDownloads() -> [DownloadedSong] {
        load([DownloadedSong].self, from: downloadsMetadataURL) ?? []
    }

    func saveDownloads(_ downloads: [DownloadedSong]) {
        save(downloads, to: downloadsMetadataURL)
    }

    func loadUser() -> User? {
        load(User.self, from: userURL)
    }

    func saveUser(_ user: User) {
        save(user, to: userURL)
    }

    func clearUser() {
        try? FileManager.default.removeItem(at: userURL)
    }

    func loadPlaybackState() -> PlaybackState? {
        load(PlaybackState.self, from: playbackStateURL)
    }

    func savePlaybackState(_ state: PlaybackState) {
        save(state, to: playbackStateURL)
    }

    func loadPlaybackPreferences() -> PlaybackPreferences {
        load(PlaybackPreferences.self, from: playbackPreferencesURL) ?? PlaybackPreferences(repeatMode: .off, shuffleEnabled: false)
    }

    func savePlaybackPreferences(repeatMode: RepeatMode, shuffleEnabled: Bool) {
        save(PlaybackPreferences(repeatMode: repeatMode, shuffleEnabled: shuffleEnabled), to: playbackPreferencesURL)
    }

    func loadCatalogCache() -> CatalogCache? {
        load(CatalogCache.self, from: catalogCacheURL)
    }

    func saveCatalogCache(_ cache: CatalogCache) {
        save(cache, to: catalogCacheURL)
    }

    func loadAudioEffects() -> AudioEffectsState? {
        load(AudioEffectsState.self, from: audioEffectsURL)
    }

    func saveAudioEffects(_ state: AudioEffectsState) {
        save(state, to: audioEffectsURL)
    }

    func preparePlaybackFile(from remoteURL: URL, cacheKey: String) async throws -> URL {
        let pathExtension = remoteURL.pathExtension.isEmpty ? "mp3" : remoteURL.pathExtension
        let sanitizedCacheKey = cacheKey
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
        let destinationURL = playbackCacheURL.appending(path: "\(sanitizedCacheKey).\(pathExtension)")
        if FileManager.default.fileExists(atPath: destinationURL.path) {
            return destinationURL
        }

        let (temporaryURL, response) = try await URLSession.shared.download(from: remoteURL)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }

        try? FileManager.default.removeItem(at: destinationURL)
        try FileManager.default.moveItem(at: temporaryURL, to: destinationURL)
        return destinationURL
    }

    func loadLyrics(title: String, artist: String) -> [LyricLine]? {
        load([LyricLine].self, from: lyricsURL(title: title, artist: artist))
    }

    func saveLyrics(_ lyrics: [LyricLine], title: String, artist: String) {
        save(lyrics, to: lyricsURL(title: title, artist: artist))
    }

    func downloadExists(_ download: DownloadedSong) -> Bool {
        FileManager.default.fileExists(atPath: download.localAudioPath)
    }

    func reconcileDownloads(_ downloads: [DownloadedSong]) -> [DownloadedSong] {
        let validDownloads = downloads.filter(downloadExists)

        let validAudioPaths = Set(validDownloads.map(\.localAudioPath))
        let validCoverPaths = Set(validDownloads.compactMap(\.localCoverPath))

        cleanupOrphanedFiles(in: downloadsURL, retaining: validAudioPaths, skippingNames: ["Covers"])
        cleanupOrphanedFiles(in: coversURL, retaining: validCoverPaths)

        return validDownloads
    }

    func removeDownload(_ download: DownloadedSong) {
        try? FileManager.default.removeItem(atPath: download.localAudioPath)
        if let localCoverPath = download.localCoverPath {
            try? FileManager.default.removeItem(atPath: localCoverPath)
        }
    }

    func cleanupPlaybackCache(olderThan age: TimeInterval) {
        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: playbackCacheURL,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else { return }

        let cutoffDate = Date().addingTimeInterval(-age)
        for fileURL in contents {
            let values = try? fileURL.resourceValues(forKeys: [.contentModificationDateKey])
            if let modifiedAt = values?.contentModificationDate, modifiedAt < cutoffDate {
                try? FileManager.default.removeItem(at: fileURL)
            }
        }
    }

    func downloadFile(
        from remoteURL: URL,
        fileName: String,
        inCoversDirectory: Bool = false,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws -> URL {
        let destinationDirectory = inCoversDirectory ? coversURL : downloadsURL
        let destinationURL = destinationDirectory.appending(path: sanitizedFileName(fileName))
        progress(0.05)
        let (temporaryURL, response) = try await URLSession.shared.download(from: remoteURL)
        guard let httpResponse = response as? HTTPURLResponse, 200..<300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }

        progress(0.9)
        try? FileManager.default.removeItem(at: destinationURL)
        try FileManager.default.moveItem(at: temporaryURL, to: destinationURL)
        progress(1)
        return destinationURL
    }

    private func sanitizedFileName(_ fileName: String) -> String {
        fileName
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
    }

    private func load<T: Decodable>(_ type: T.Type, from url: URL) -> T? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? decoder.decode(type, from: data)
    }

    private func save<T: Encodable>(_ value: T, to url: URL) {
        guard let data = try? encoder.encode(value) else { return }
        try? data.write(to: url, options: .atomic)
    }

    private func cleanupOrphanedFiles(in directory: URL, retaining retainedPaths: Set<String>, skippingNames: Set<String> = []) {
        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else { return }

        for fileURL in contents {
            if skippingNames.contains(fileURL.lastPathComponent) {
                continue
            }

            let values = try? fileURL.resourceValues(forKeys: [.isDirectoryKey])
            if values?.isDirectory == true {
                continue
            }

            if !retainedPaths.contains(fileURL.path) {
                try? FileManager.default.removeItem(at: fileURL)
            }
        }
    }

    private func lyricsURL(title: String, artist: String) -> URL {
        let safeName = "\(title)_\(artist)"
            .replacingOccurrences(of: "[^a-zA-Z0-9]", with: "_", options: .regularExpression)
            .prefix(100)
        return lyricsCacheURL.appending(path: "\(safeName).json")
    }
}
