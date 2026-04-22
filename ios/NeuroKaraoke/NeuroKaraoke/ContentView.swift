import SwiftUI
import WebKit

struct ContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var model: AppModel
    @State private var selectedSong: Song?
    @State private var searchText = ""
    @State private var selectedLibraryPlaylist: Playlist?
    @State private var selectedExplorePlaylist: Playlist?
    @State private var soundbiteSearch = ""

    var body: some View {
        ZStack(alignment: .bottom) {
            themeBackground
                .ignoresSafeArea()

            if model.isReady {
                TabView(selection: $model.selectedTab) {
                    NavigationStack {
                        HomeTab(model: model, selectedSong: $selectedSong)
                    }
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    .tag(AppTab.home)

                    NavigationStack {
                        SearchTab(model: model, selectedSong: $selectedSong, searchText: $searchText)
                    }
                    .tabItem {
                        Label("Search", systemImage: "magnifyingglass")
                    }
                    .tag(AppTab.search)

                    NavigationStack {
                        ExploreTab(model: model, selectedPlaylist: $selectedExplorePlaylist)
                    }
                    .tabItem {
                        Label("Explore", systemImage: "safari.fill")
                    }
                    .tag(AppTab.explore)

                    NavigationStack {
                        LibraryTab(
                            model: model,
                            selectedSong: $selectedSong,
                            selectedPlaylist: $selectedLibraryPlaylist
                        )
                    }
                    .tabItem {
                        Label("Library", systemImage: "square.stack.fill")
                    }
                    .tag(AppTab.library)

                    NavigationStack {
                        RadioTab(model: model, selectedSong: $selectedSong)
                    }
                    .tabItem {
                        Label("Radio", systemImage: "dot.radiowaves.left.and.right")
                    }
                    .tag(AppTab.radio)

                    NavigationStack {
                        SoundbitesTab(model: model, searchText: $soundbiteSearch)
                    }
                    .tabItem {
                        Label("Soundbites", systemImage: "waveform.badge.mic")
                    }
                    .tag(AppTab.soundbites)

                    NavigationStack {
                        SetlistsTab(model: model, selectedSong: $selectedSong)
                    }
                    .tabItem {
                        Label("Setlists", systemImage: "music.note.list")
                    }
                    .tag(AppTab.setlists)

                    NavigationStack {
                        ArtistsTab(model: model, selectedSong: $selectedSong)
                    }
                    .tabItem {
                        Label("Artists", systemImage: "person.3.fill")
                    }
                    .tag(AppTab.artists)
                }
                .tint(theme.primary)

                if let currentSong = model.currentSong {
                    MiniPlayer(song: currentSong, model: model)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 60)
                        .onTapGesture {
                            selectedSong = currentSong
                        }
                }
            } else {
                SetupView(progress: model.setupProgress, errorMessage: model.errorMessage)
            }
        }
        .task {
            if !ProcessInfo.processInfo.isRunningForPreviews {
                await model.start()
            }
        }
        .sheet(item: $selectedSong) { song in
            PlayerView(song: song, model: model)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(item: $selectedLibraryPlaylist) { playlist in
            NavigationStack {
                UserPlaylistDetailView(
                    playlist: playlist,
                    model: model,
                    selectedSong: $selectedSong
                )
            }
        }
        .sheet(item: $selectedExplorePlaylist) { playlist in
            NavigationStack {
                PlaylistDetailView(playlist: playlist, model: model, selectedSong: $selectedSong)
            }
        }
    }

    private var theme: AppTheme {
        AppTheme.forSinger(model.currentSong?.singer, colorScheme: colorScheme)
    }

    private var themeBackground: some View {
        LinearGradient(
            colors: [theme.backgroundTop, theme.backgroundBottom],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .overlay(alignment: .topTrailing) {
            Circle()
                .fill(theme.primary.opacity(0.18))
                .frame(width: 220, height: 220)
                .blur(radius: 12)
                .offset(x: 80, y: -20)
        }
        .overlay(alignment: .bottomLeading) {
            Circle()
                .fill(theme.secondary.opacity(0.16))
                .frame(width: 260, height: 260)
                .blur(radius: 18)
                .offset(x: -80, y: 80)
        }
    }
}

private struct HomeTab: View {
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HeroCard(
                    latestPlaylist: model.playlists.first,
                    distribution: model.coverDistribution
                )

                if !model.trendingSongs.isEmpty {
                    sectionHeader("Trending This Week")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(model.trendingSongs.prefix(8)) { song in
                                SongPoster(song: song)
                                    .frame(width: 150)
                                    .onTapGesture {
                                        model.play(song, in: model.trendingSongs, source: .persistedQueue)
                                        selectedSong = song
                                    }
                            }
                        }
                    }
                }

                if let latestPlaylist = model.playlists.first {
                    sectionHeader(latestPlaylist.title.isEmpty ? "Latest Setlist" : latestPlaylist.title)
                    VStack(spacing: 10) {
                        ForEach(model.songs(for: latestPlaylist).prefix(6)) { song in
                            SongRow(song: song, model: model) {
                                model.play(song, in: model.songs(for: latestPlaylist), source: .playlist(latestPlaylist.id))
                                selectedSong = song
                            }
                        }
                    }
                }

                if let distribution = model.coverDistribution {
                    sectionHeader("Cover Distribution")
                    DistributionCard(distribution: distribution)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 140)
        }
        .navigationTitle("Neuro Karaoke")
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.title3.weight(.bold))
                .foregroundStyle(.primary)
            Spacer()
        }
    }
}

private struct SearchTab: View {
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?
    @Binding var searchText: String

    var body: some View {
        let results = model.filteredSongs(query: searchText)

        List(results) { song in
            SongRow(song: song, model: model) {
                model.play(song, in: results, source: .persistedQueue)
                selectedSong = song
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.clear)
        .searchable(text: $searchText, prompt: "Search songs, artists, playlists")
        .navigationTitle("Search")
    }
}

private struct ExploreTab: View {
    @ObservedObject var model: AppModel
    @Binding var selectedPlaylist: Playlist?
    @State private var searchText = ""

    var body: some View {
        let items = model.publicPlaylists.filter { playlist in
            searchText.isEmpty ||
            playlist.title.localizedCaseInsensitiveContains(searchText) ||
            playlist.description.localizedCaseInsensitiveContains(searchText)
        }

        List {
            Section {
                Text("Public playlists created by the NeuroKaraoke community.")
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            }

            if model.isLoadingPublicPlaylists {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
            } else if items.isEmpty {
                EmptyState(icon: "safari", title: "No playlists found", message: searchText.isEmpty ? "No public playlists are available right now." : "Try a different search.")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(items) { playlist in
                    Button {
                        selectedPlaylist = playlist
                    } label: {
                        PlaylistRow(playlist: playlist)
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.clear)
        .searchable(text: $searchText, prompt: "Search playlists")
        .navigationTitle("Explore")
        .task {
            if model.publicPlaylists.isEmpty {
                await model.loadPublicPlaylists()
            }
        }
    }
}

private struct SetlistsTab: View {
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        List(model.playlists) { playlist in
            NavigationLink(value: playlist) {
                PlaylistRow(playlist: playlist)
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle("Setlists")
        .navigationDestination(for: Playlist.self) { playlist in
            PlaylistDetailView(playlist: playlist, model: model, selectedSong: $selectedSong)
        }
    }
}

private struct ArtistsTab: View {
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        List(model.artists) { artist in
            NavigationLink(value: artist) {
                ArtistRow(artist: artist)
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle("Artists")
        .navigationDestination(for: Artist.self) { artist in
            ArtistDetailView(artist: artist, model: model, selectedSong: $selectedSong)
        }
    }
}

private struct PlaylistDetailView: View {
    let playlist: Playlist
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        let playlistSongs = model.songs(for: playlist)
        let downloadedCount = model.downloadedSongCount(for: playlist)
        let isDownloading = model.activePlaylistDownloadIDs.contains(playlist.id)

        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                BannerArtwork(imageURL: playlist.coverURL ?? playlist.previewCoverURLs.first)
                    .frame(height: 220)

                Text(playlist.title)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)

                if !playlist.description.isEmpty {
                    Text(playlist.description)
                        .foregroundStyle(.secondary)
                }

                Text("\(playlistSongs.count) songs")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)

                HStack(spacing: 12) {
                    Button {
                        Task {
                            await model.downloadPlaylist(playlist)
                        }
                    } label: {
                        Label(
                            isDownloading ? "Downloading…" : (downloadedCount == playlistSongs.count && !playlistSongs.isEmpty ? "Downloaded" : "Download Setlist"),
                            systemImage: isDownloading ? "arrow.down.circle.fill" : "arrow.down.circle"
                        )
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isDownloading || playlistSongs.isEmpty || downloadedCount == playlistSongs.count)

                    if downloadedCount > 0 {
                        Text("\(downloadedCount)/\(playlistSongs.count) offline")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(.secondary)
                    }
                }

                VStack(spacing: 10) {
                    ForEach(playlistSongs) { song in
                        SongRow(song: song, model: model) {
                            model.play(song, in: playlistSongs, source: .playlist(playlist.id))
                            selectedSong = song
                        }
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 130)
        }
        .navigationTitle("Setlist")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ArtistDetailView: View {
    let artist: Artist
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        let songs = model.songs(for: artist)

        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                BannerArtwork(imageURL: artist.imageURL)
                    .frame(height: 220)

                Text(artist.name)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.primary)

                if !artist.summary.isEmpty {
                    Text(artist.summary)
                        .foregroundStyle(.secondary)
                }

                Text("\(songs.count) matching songs")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)

                VStack(spacing: 10) {
                    ForEach(songs) { song in
                        SongRow(song: song, model: model) {
                            model.play(song, in: songs, source: .persistedQueue)
                            selectedSong = song
                        }
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 130)
        }
        .navigationTitle("Artist")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LibraryTab: View {
    enum Section: String, CaseIterable, Identifiable {
        case favorites = "Favorites"
        case playlists = "Playlists"
        case downloads = "Downloads"

        var id: String { rawValue }
    }

    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?
    @Binding var selectedPlaylist: Playlist?
    @State private var selectedSection: Section = .favorites
    @State private var newPlaylistName = ""
    @State private var newPlaylistDescription = ""
    @State private var showingCreatePlaylist = false
    @State private var showingAbout = false
    @State private var showingDeleteAllDownloadsConfirmation = false

    var body: some View {
        VStack(spacing: 16) {
            AccountCard(model: model)
                .padding(.horizontal, 16)

            Picker("Library", selection: $selectedSection) {
                ForEach(Section.allCases) { section in
                    Text(section.rawValue).tag(section)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)

            switch selectedSection {
            case .favorites:
                List(model.favoriteSongs) { song in
                    SongRow(song: song, model: model) {
                        model.play(song, in: model.favoriteSongs, source: .persistedQueue)
                        selectedSong = song
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                .overlay {
                    if model.favoriteSongs.isEmpty {
                        EmptyState(icon: "heart.fill", title: "No favorites yet", message: "Use the heart action on any song to build your favorites.")
                    }
                }
                .safeAreaInset(edge: .bottom) {
                    Color.clear.frame(height: 96)
                }
            case .playlists:
                List {
                    ForEach(model.userPlaylists) { playlist in
                        Button {
                            selectedPlaylist = playlist
                        } label: {
                            PlaylistRow(playlist: playlist)
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.clear)
                        .swipeActions {
                            Button(role: .destructive) {
                                model.deletePlaylist(playlist)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
                .overlay {
                    if model.userPlaylists.isEmpty {
                        EmptyState(icon: "music.note.list", title: "No playlists yet", message: "Create a playlist and add songs from the row action menu.")
                    }
                }
                .safeAreaInset(edge: .bottom) {
                    HStack {
                        Spacer()
                        Button {
                            showingCreatePlaylist = true
                        } label: {
                            Image(systemName: "plus")
                                .font(.headline)
                                .foregroundStyle(Color(.label))
                                .frame(width: 52, height: 52)
                                .background(Circle().fill(Color(.systemBackground)))
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 36)
                }
            case .downloads:
                List {
                    if !model.downloadedSongs.isEmpty {
                        SwiftUI.Section {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("\(model.downloadedSongs.count) songs · \(model.totalDownloadedSizeDescription())")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                Button("Delete All") {
                                    showingDeleteAllDownloadsConfirmation = true
                                }
                                .font(.footnote.weight(.medium))
                            }
                            .padding(.vertical, 4)
                            .listRowBackground(Color.clear)
                        }
                    }

                    ForEach(model.downloadedSongs) { download in
                        SongRow(song: download.song, model: model) {
                            model.playDownload(download)
                            selectedSong = download.song
                        }
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .swipeActions {
                            Button(role: .destructive) {
                                model.removeDownload(download)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
                .overlay {
                    if model.downloadedSongs.isEmpty {
                        EmptyState(icon: "arrow.down.circle.fill", title: "No downloads yet", message: "Download songs from the row action menu for offline playback.")
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle("Library")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingAbout = true
                } label: {
                    Image(systemName: "info.circle")
                        .foregroundStyle(.primary)
                }
            }
        }
        .sheet(isPresented: $showingCreatePlaylist) {
            NavigationStack {
                Form {
                    TextField("Playlist name", text: $newPlaylistName)
                    TextField("Description", text: $newPlaylistDescription)
                }
                .navigationTitle("New Playlist")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            showingCreatePlaylist = false
                            newPlaylistName = ""
                            newPlaylistDescription = ""
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Create") {
                            model.createPlaylist(name: newPlaylistName, description: newPlaylistDescription)
                            showingCreatePlaylist = false
                            newPlaylistName = ""
                            newPlaylistDescription = ""
                        }
                        .disabled(newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
        }
        .sheet(isPresented: $showingAbout) {
            NavigationStack {
                AboutView()
            }
        }
        .confirmationDialog("Delete all downloads?", isPresented: $showingDeleteAllDownloadsConfirmation, titleVisibility: .visible) {
            Button("Delete All Downloads", role: .destructive) {
                model.removeAllDownloads()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will remove all downloaded songs and cached cover files from the device.")
        }
    }
}

private struct AccountCard: View {
    @ObservedObject var model: AppModel
    @State private var showingWebSignIn = false

    var body: some View {
        HStack(spacing: 12) {
            if let avatarURL = model.currentUser?.avatarURL {
                BannerArtwork(imageURL: avatarURL)
                    .frame(width: 56, height: 56)
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(model.currentUser?.displayName ?? "Not signed in")
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(model.currentUser == nil ? "Sign in to sync favorites and playlists" : "Discord + NeuroKaraoke sync")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if model.currentUser == nil {
                Menu {
                    Button("Discord Sign In") {
                        model.signIn()
                    }

                    Button("Web Sign In") {
                        showingWebSignIn = true
                    }
                } label: {
                    Text("Sign In")
                        .frame(minWidth: 72)
                }
                .buttonStyle(.borderedProminent)
                .tint(.accentColor)
                .foregroundStyle(.white)
            } else {
                Menu {
                    Button {
                        Task { await model.syncLibraryFromServer() }
                    } label: {
                        Label(model.isSyncingLibrary ? "Syncing…" : "Sync Now", systemImage: "arrow.triangle.2.circlepath")
                    }

                    Button(role: .destructive) {
                        model.logout()
                    } label: {
                        Label("Log Out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.title2)
                        .foregroundStyle(.primary)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial.opacity(0.4))
        )
        .sheet(isPresented: $showingWebSignIn) {
            NavigationStack {
                WebAuthSheet(model: model)
            }
        }
    }
}

private struct WebAuthSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var model: AppModel
    @State private var isLoading = true
    @State private var currentURL = ""
    @State private var jwtFound = false

    var body: some View {
        ZStack {
            WebAuthView(
                startURL: model.webViewSignInURL(),
                onPageStateChange: { url, loading in
                    currentURL = url?.absoluteString ?? ""
                    isLoading = loading
                },
                onTokenFound: { token in
                    guard !jwtFound else { return }
                    jwtFound = true
                    if model.handleJwtFromWebView(token) {
                        dismiss()
                    } else {
                        jwtFound = false
                    }
                }
            )

            if isLoading && !jwtFound {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Loading sign-in page...")
                        .font(.body.weight(.medium))
                    Text("This may take a while the first time.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(.regularMaterial)
                )
            }

            if jwtFound {
                Color.black.opacity(0.25)
                    .ignoresSafeArea()
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Signing you in...")
                        .font(.body.weight(.medium))
                }
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(.regularMaterial)
                )
            }
        }
        .navigationTitle("Web Sign In")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Close") {
                    dismiss()
                }
            }
        }
    }
}

private struct WebAuthView: UIViewRepresentable {
    let startURL: URL?
    let onPageStateChange: (URL?, Bool) -> Void
    let onTokenFound: (String) -> Void
    private let store = SharedAuthWebViewStore.shared

    func makeCoordinator() -> Coordinator {
        Coordinator(onPageStateChange: onPageStateChange, onTokenFound: onTokenFound)
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = store.webView
        webView.allowsBackForwardNavigationGestures = true
        webView.navigationDelegate = context.coordinator
        context.coordinator.configure(webView: webView, startURL: startURL)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.webView = webView
        webView.navigationDelegate = context.coordinator
        context.coordinator.configure(webView: webView, startURL: startURL)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        weak var webView: WKWebView?
        private let onPageStateChange: (URL?, Bool) -> Void
        private let onTokenFound: (String) -> Void
        private var pollAttempt = 0
        private var jwtFound = false
        private let maxPollAttempts = 20

        init(
            onPageStateChange: @escaping (URL?, Bool) -> Void,
            onTokenFound: @escaping (String) -> Void
        ) {
            self.onPageStateChange = onPageStateChange
            self.onTokenFound = onTokenFound
        }

        func configure(webView: WKWebView, startURL: URL?) {
            self.webView = webView
            if webView.url == nil, let startURL {
                webView.load(URLRequest(url: startURL))
                return
            }

            onPageStateChange(webView.url, webView.isLoading)
            if !webView.isLoading {
                startPollingForJWTIfNeeded(in: webView)
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            onPageStateChange(webView.url, true)
        }

        func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
            onPageStateChange(webView.url, webView.isLoading)
            startPollingForJWTIfNeeded(in: webView)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            onPageStateChange(webView.url, false)
            startPollingForJWTIfNeeded(in: webView)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            onPageStateChange(webView.url, false)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            onPageStateChange(webView.url, false)
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            if let scheme = navigationAction.request.url?.scheme, !scheme.hasPrefix("http") {
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }

        private func startPollingForJWTIfNeeded(in webView: WKWebView) {
            guard
                let absoluteString = webView.url?.absoluteString,
                absoluteString.contains("neurokaraoke.com"),
                !jwtFound
            else { return }

            pollAttempt = 0
            pollForJWT(in: webView)
        }

        private func pollForJWT(in webView: WKWebView) {
            guard !jwtFound, pollAttempt <= maxPollAttempts else { return }
            pollAttempt += 1

            let script = "(function() { try { return localStorage.getItem('authToken') || ''; } catch(e) { return ''; } })();"
            webView.evaluateJavaScript(script) { [weak self] result, _ in
                guard let self else { return }

                let token = (result as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if token.hasPrefix("eyJ") {
                    self.jwtFound = true
                    self.onTokenFound(token)
                    return
                }

                DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [weak self, weak webView] in
                    guard let self, let webView else { return }
                    self.pollForJWT(in: webView)
                }
            }
        }
    }
}

private struct AboutView: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Neuro Karaoke")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)

                aboutCard(
                    title: "About",
                    lines: [
                        "This iOS app is a fan-made mobile client for NeuroKaraoke.",
                        "It brings karaoke browsing, search, playback, library tools, radio, lyrics, and soundbites to iPhone."
                    ]
                )

                aboutCard(
                    title: "Features",
                    lines: [
                        "Browse official setlists and public playlists",
                        "Search across all songs",
                        "Background playback with queue, repeat, shuffle, and sleep timer",
                        "Favorites, downloads, user playlists, radio, lyrics, and soundbites"
                    ]
                )

                aboutCard(
                    title: "Credits",
                    lines: [
                        "Soul: original site creator and developer",
                        "Aferil: Android app developer",
                        "Community artists: artwork used with permission on NeuroKaraoke"
                    ]
                )

                VStack(alignment: .leading, spacing: 12) {
                    Text("Links")
                        .font(.headline)
                        .foregroundStyle(.primary)

                    linkButton("Website", url: "https://neurokaraoke.com")
                    linkButton("GitHub Repo", url: "https://github.com/AferilVT/neuro-karaoke-wrapper")
                    linkButton("Privacy Policy", url: "https://neurokaraoke.com/privacy-policy")
                }
                .padding(18)
                .background(RoundedRectangle(cornerRadius: 24).fill(.ultraThinMaterial.opacity(0.35)))
            }
            .padding(16)
            .padding(.bottom, 40)
        }
        .background(Color(.systemBackground))
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func aboutCard(title: String, lines: [String]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.primary)
            ForEach(lines, id: \.self) { line in
                Text(line)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(18)
        .background(RoundedRectangle(cornerRadius: 24).fill(.ultraThinMaterial.opacity(0.35)))
    }

    private func linkButton(_ title: String, url: String) -> some View {
        Button {
            if let url = URL(string: url) {
                openURL(url)
            }
        } label: {
            HStack {
                Text(title)
                Spacer()
                Image(systemName: "arrow.up.right.square")
            }
        }
        .buttonStyle(.bordered)
        .tint(.accentColor)
    }
}

private struct UserPlaylistDetailView: View {
    let playlist: Playlist
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        let songs = model.userPlaylists.first(where: { $0.id == playlist.id })?.songs ?? playlist.songs
        let downloadedCount = model.downloadedSongCount(forUserPlaylist: playlist)
        let isDownloading = model.activePlaylistDownloadIDs.contains(playlist.id)

        List {
            if !songs.isEmpty {
                Section {
                    Button {
                        Task {
                            await model.downloadUserPlaylist(playlist)
                        }
                    } label: {
                        HStack {
                            Label(
                                isDownloading ? "Downloading…" : (downloadedCount == songs.count ? "Playlist Downloaded" : "Download Playlist"),
                                systemImage: isDownloading ? "arrow.down.circle.fill" : "arrow.down.circle"
                            )
                            Spacer()
                            if downloadedCount > 0 {
                                Text("\(downloadedCount)/\(songs.count)")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .disabled(isDownloading || downloadedCount == songs.count)
                }
                .listRowBackground(Color.clear)
            }

            ForEach(songs) { song in
                SongRow(song: song, model: model) {
                    model.play(song, in: songs, source: .playlist(playlist.id))
                    selectedSong = song
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .swipeActions {
                    Button(role: .destructive) {
                        model.removeSong(song, from: playlist)
                    } label: {
                        Label("Remove", systemImage: "trash")
                    }
                }
            }
        }
        .overlay {
            if songs.isEmpty {
                EmptyState(icon: "music.note.list", title: "No songs yet", message: "Add songs to this playlist from the row action menu.")
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .navigationTitle(playlist.title)
    }
}

private struct RadioTab: View {
    @ObservedObject var model: AppModel
    @Binding var selectedSong: Song?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let state = model.radioState {
                    HStack {
                        Label("\(state.listenerCount) listeners", systemImage: "headphones")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(state.offline ? "Offline" : "Live")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(state.offline ? .pink : .green)
                    }

                    if let current = state.current {
                        sectionHeader("Now Playing")
                        VStack(alignment: .leading, spacing: 12) {
                            BannerArtwork(imageURL: current.coverURL)
                                .frame(height: 220)
                            Text(current.title)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(.primary)
                            Text(current.originalArtists.joined(separator: ", "))
                                .foregroundStyle(.secondary)
                            Button {
                                if model.isRadioPlaying {
                                    model.stopRadioPlayback(clearCurrent: false)
                                } else {
                                    model.playRadio()
                                    selectedSong = current.song
                                }
                            } label: {
                                Label(model.isRadioPlaying ? "Stop Listening" : "Listen Live", systemImage: model.isRadioPlaying ? "stop.fill" : "play.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(model.isRadioPlaying ? .pink : AppTheme.forSinger(current.song.singer).primary)
                        }
                        .padding(16)
                        .background(RoundedRectangle(cornerRadius: 24).fill(.ultraThinMaterial.opacity(0.45)))
                    }

                    if !state.upcoming.isEmpty {
                        sectionHeader("Up Next")
                        VStack(spacing: 10) {
                            ForEach(state.upcoming) { radioSong in
                                RadioSongRow(song: radioSong)
                            }
                        }
                    }

                    if !state.history.isEmpty {
                        sectionHeader("Recently Played")
                        VStack(spacing: 10) {
                            ForEach(state.history) { radioSong in
                                RadioSongRow(song: radioSong)
                            }
                        }
                    }
                } else {
                    EmptyState(icon: "dot.radiowaves.left.and.right", title: "Loading radio", message: "Fetching the current stream state.")
                }
            }
            .padding(16)
            .padding(.bottom, 140)
        }
        .navigationTitle("Radio")
        .task {
            await model.refreshRadioState()
            model.startRadioPolling()
        }
        .onDisappear {
            if !model.isRadioPlaying {
                model.stopRadioPolling()
            }
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.title3.weight(.bold))
                .foregroundStyle(.primary)
            Spacer()
        }
    }
}

private struct SoundbitesTab: View {
    enum SortOption: String, CaseIterable, Identifiable {
        case mostPlayed = "Most Played"
        case newest = "Newest"
        case titleAZ = "Title A-Z"
        case shortest = "Shortest"
        case longest = "Longest"

        var id: String { rawValue }
    }

    @ObservedObject var model: AppModel
    @Binding var searchText: String
    @State private var selectedTag = -1
    @State private var sortOption: SortOption = .mostPlayed

    private let tagFilters = [(-1, "All"), (0, "Neuro"), (1, "Evil"), (2, "Vedal"), (3, "Other")]

    var body: some View {
        let filtered = model.soundbites.filter { selectedTag == -1 || $0.tag == selectedTag }
        let displayItems = sorted(filtered)

        List {
            Section {
                Text("A collection of stream soundbites featuring Neuro, Evil, and friends.")
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            }

            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(tagFilters, id: \.0) { tag, label in
                            Button {
                                selectedTag = tag
                            } label: {
                                Text(label)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule().fill(selectedTag == tag ? Color.accentColor : Color(.secondarySystemBackground))
                                    )
                                    .foregroundStyle(selectedTag == tag ? .white : .primary)
                            }
                        }
                    }
                }
                .listRowBackground(Color.clear)

                Picker("Sort", selection: $sortOption) {
                    ForEach(SortOption.allCases) { option in
                        Text(option.rawValue).tag(option)
                    }
                }
                .pickerStyle(.menu)
                .listRowBackground(Color.clear)
            }

            if model.isLoadingSoundbites {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
            } else if displayItems.isEmpty {
                EmptyState(icon: "waveform.badge.mic", title: "No soundbites found", message: "Try a different search term or filter.")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                    .listRowBackground(Color.clear)
            } else {
                ForEach(displayItems) { soundbite in
                    SoundbiteRow(
                        soundbite: soundbite,
                        isPlaying: model.playingSoundbiteID == soundbite.id
                    ) {
                        model.playSoundbite(soundbite)
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .onAppear {
                        if soundbite.id == displayItems.suffix(5).first?.id,
                           model.soundbites.count < model.totalSoundbites,
                           !model.isLoadingMoreSoundbites {
                            Task {
                                await model.loadSoundbites(search: searchText, reset: false)
                            }
                        }
                    }
                }

                if model.isLoadingMoreSoundbites {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .listRowBackground(Color.clear)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.clear)
        .navigationTitle("Soundbites")
        .searchable(text: $searchText, prompt: "Search soundbites")
        .task {
            await model.loadSoundbites()
        }
        .onChange(of: searchText) { _, newValue in
            Task {
                await model.loadSoundbites(search: newValue, reset: true)
            }
        }
    }

    private func sorted(_ items: [Soundbite]) -> [Soundbite] {
        switch sortOption {
        case .mostPlayed:
            return items.sorted { $0.playCount > $1.playCount }
        case .newest:
            return items.sorted { ($0.uploadedAt ?? "") > ($1.uploadedAt ?? "") }
        case .titleAZ:
            return items.sorted { $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending }
        case .shortest:
            return items.sorted { $0.duration < $1.duration }
        case .longest:
            return items.sorted { $0.duration > $1.duration }
        }
    }
}

struct SetupView: View {
    let progress: SetupProgress
    let errorMessage: String?

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image("NeuroLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 72, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            Text("Neuro Karaoke")
                .font(.system(size: 30, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)

            Text(progress.status)
                .font(.headline)
                .foregroundStyle(.primary)

            Text(progress.detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            ProgressView(value: progress.fraction)
                .tint(.accentColor)
                .frame(maxWidth: 260)

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(.pink.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            LinearGradient(
                colors: [
                    Color(red: 0.07, green: 0.08, blue: 0.11),
                    Color(red: 0.12, green: 0.14, blue: 0.19)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
        )
    }
}

@MainActor
private final class SharedAuthWebViewStore {
    static let shared = SharedAuthWebViewStore()

    let webView: WKWebView

    private init() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        webView = WKWebView(frame: .zero, configuration: configuration)
    }
}

private struct HeroCard: View {
    @Environment(\.colorScheme) private var colorScheme
    let latestPlaylist: Playlist?
    let distribution: CoverDistribution?

    var body: some View {
        let theme = AppTheme.forSinger(nil, colorScheme: colorScheme)

        VStack(alignment: .leading, spacing: 18) {
            Text("Cross-platform karaoke player for neurokaraoke.com")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)

            if let latestPlaylist {
                HStack(spacing: 14) {
                    BannerArtwork(imageURL: latestPlaylist.coverURL ?? latestPlaylist.previewCoverURLs.first)
                        .frame(width: 86, height: 86)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Featured Setlist")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(latestPlaylist.title)
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Text("\(latestPlaylist.songCount) songs")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }
            }

            if let distribution {
                HStack {
                    Text("\(distribution.totalSongs) covers")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Spacer()
                    Label("Neuro \(distribution.neuroCount)", systemImage: "waveform")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(theme.primary)
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(.ultraThinMaterial.opacity(0.55))
                .overlay {
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .stroke(
                            LinearGradient(
                                colors: [theme.primary.opacity(0.7), theme.secondary.opacity(0.35)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.2
                        )
                }
        )
    }
}

private struct DistributionCard: View {
    let distribution: CoverDistribution

    var body: some View {
        VStack(spacing: 12) {
            DistributionBar(title: "Neuro", value: distribution.neuroCount, total: distribution.totalSongs, color: .cyan)
            DistributionBar(title: "Evil", value: distribution.evilCount, total: distribution.totalSongs, color: .pink)
            DistributionBar(title: "Duet", value: distribution.duetCount, total: distribution.totalSongs, color: .purple)
            DistributionBar(title: "Other", value: distribution.otherCount, total: distribution.totalSongs, color: .gray)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.ultraThinMaterial.opacity(0.48))
        )
    }
}

private struct DistributionBar: View {
    let title: String
    let value: Int
    let total: Int
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .foregroundStyle(.primary)
                Spacer()
                Text("\(value)")
                    .foregroundStyle(.secondary)
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color(.tertiarySystemFill))
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(color.gradient)
                        .frame(width: max(16, geometry.size.width * CGFloat(Double(value) / Double(max(total, 1)))))
                }
            }
            .frame(height: 12)
        }
    }
}

private struct SongPoster: View {
    let song: Song

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            BannerArtwork(imageURL: song.coverURL)
                .frame(width: 150, height: 150)
            Text(song.title)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(2)
            Text(song.artist)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

private struct SongRow: View {
    let song: Song
    @ObservedObject var model: AppModel
    let action: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            BannerArtwork(imageURL: song.coverURL)
                .frame(width: 58, height: 58)

            VStack(alignment: .leading, spacing: 4) {
                Text(song.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(song.artist)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let playlistName = song.playlistName {
                    Text(playlistName)
                        .font(.caption)
                        .foregroundStyle(.secondary.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let progress = model.downloadProgress[song.id] {
                    ProgressView(value: progress)
                        .progressViewStyle(.linear)
                        .tint(.accentColor)
                }
            }

            if model.isDownloaded(song.id) {
                Image(systemName: "arrow.down.circle.fill")
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
            }

            Button(action: action) {
                Image(systemName: "play.fill")
                    .font(.headline)
                    .foregroundStyle(AppTheme.forSinger(song.singer).primary)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Color(.secondarySystemBackground)))
            }
            .buttonStyle(.plain)

            Menu {
                Button {
                    model.toggleFavorite(song)
                } label: {
                    Label(model.isFavorite(song) ? "Remove Favorite" : "Add Favorite", systemImage: model.isFavorite(song) ? "heart.slash" : "heart")
                }

                if !model.isDownloaded(song.id) {
                    Button {
                        Task {
                            await model.downloadSong(song)
                        }
                    } label: {
                        Label("Download", systemImage: "arrow.down.circle")
                    }
                } else {
                    Button(role: .destructive) {
                        model.removeDownload(songID: song.id)
                    } label: {
                        Label("Delete Download", systemImage: "trash")
                    }
                }

                if !model.userPlaylists.isEmpty {
                    ForEach(model.userPlaylists) { playlist in
                        Button {
                            model.addSong(song, to: playlist)
                        } label: {
                            Label("Add to \(playlist.title)", systemImage: "text.badge.plus")
                        }
                    }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.ultraThinMaterial.opacity(0.42))
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: action)
    }
}

private struct PlaylistRow: View {
    let playlist: Playlist

    var body: some View {
        HStack(spacing: 14) {
            BannerArtwork(imageURL: playlist.coverURL ?? playlist.previewCoverURLs.first)
                .frame(width: 72, height: 72)

            VStack(alignment: .leading, spacing: 6) {
                Text(playlist.title.isEmpty ? "Untitled Playlist" : playlist.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text("\(playlist.isUserPlaylist ? playlist.songs.count : playlist.songCount) songs")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(.vertical, 6)
    }
}

private struct ArtistRow: View {
    let artist: Artist

    var body: some View {
        HStack(spacing: 14) {
            BannerArtwork(imageURL: artist.imageURL)
                .frame(width: 72, height: 72)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 6) {
                Text(artist.name)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text("\(artist.songCount) songs")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(.vertical, 6)
    }
}

private struct RadioSongRow: View {
    let song: RadioSong

    var body: some View {
        HStack(spacing: 12) {
            BannerArtwork(imageURL: song.coverURL)
                .frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 4) {
                Text(song.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(song.originalArtists.joined(separator: ", "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text(formatDuration(song.duration))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.ultraThinMaterial.opacity(0.36))
        )
    }

    private func formatDuration(_ duration: Int) -> String {
        "\(duration / 60):" + String(format: "%02d", duration % 60)
    }
}

private struct SoundbiteRow: View {
    let soundbite: Soundbite
    let isPlaying: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    BannerArtwork(imageURL: soundbite.imageURL)
                        .frame(width: 54, height: 54)
                        .clipShape(Circle())
                    if isPlaying {
                        Circle()
                            .fill(.black.opacity(0.45))
                            .frame(width: 54, height: 54)
                        Image(systemName: "speaker.wave.2.fill")
                            .foregroundStyle(.white)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(soundbite.displayTitle)
                        .font(.headline)
                        .foregroundStyle(isPlaying ? .cyan : .primary)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        Text(soundbite.tagLabel)
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(tagColor.opacity(0.18)))
                            .foregroundStyle(tagColor)
                        Text("\(soundbite.duration)s")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Label("\(soundbite.playCount)", systemImage: "play.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                Image(systemName: isPlaying ? "stop.fill" : "play.fill")
                    .foregroundStyle(isPlaying ? .cyan : .secondary)
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }

    private var tagColor: Color {
        switch soundbite.tag {
        case 0: return .cyan
        case 1: return .pink
        case 2: return .green
        default: return .gray
        }
    }
}

private struct EmptyState: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 54))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.title3.weight(.bold))
                .foregroundStyle(.primary)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 280)
        }
    }
}

private struct MiniPlayer: View {
    let song: Song
    @ObservedObject var model: AppModel

    var body: some View {
        HStack(spacing: 12) {
            BannerArtwork(imageURL: song.coverURL)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 4) {
                Text(song.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(song.artist)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Button {
                model.playPrevious()
            } label: {
                Image(systemName: "backward.fill")
                    .foregroundStyle(.primary)
            }
            Button {
                model.togglePlayback()
            } label: {
                Image(systemName: model.isPlaying ? "pause.fill" : "play.fill")
                    .foregroundStyle(Color(.label))
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Color(.systemBackground)))
            }
            Button {
                model.playNext()
            } label: {
                Image(systemName: "forward.fill")
                    .foregroundStyle(.primary)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.ultraThinMaterial.opacity(0.75))
                .overlay {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                }
        )
    }
}

private struct PlayerView: View {
    @Environment(\.colorScheme) private var colorScheme
    let song: Song
    @ObservedObject var model: AppModel
    @State private var showLyrics = false
    @State private var showQueue = false
    @State private var showSleepTimer = false
    @State private var showEqualizer = false
    @State private var isScrubbing = false
    @State private var scrubProgress = 0.0

    var body: some View {
        let activeSong = model.currentSong ?? song
        let theme = AppTheme.forSinger(activeSong.singer, colorScheme: colorScheme)

        ZStack {
            LinearGradient(colors: [theme.backgroundTop, theme.backgroundBottom], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 24) {
                Capsule()
                    .fill(Color.primary.opacity(0.18))
                    .frame(width: 48, height: 5)
                    .padding(.top, 8)

                BannerArtwork(imageURL: activeSong.coverURL)
                    .frame(maxWidth: 320, maxHeight: 320)
                    .shadow(color: theme.primary.opacity(0.35), radius: 30)

                VStack(spacing: 8) {
                    Text(activeSong.title)
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.center)
                    Text(activeSong.artist)
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 8) {
                    playerActionButton("Effects", systemImage: "slider.horizontal.3", tint: theme.primary) {
                        showEqualizer = true
                    }

                    playerActionButton("Lyrics", systemImage: "quote.bubble", tint: theme.primary) {
                        showLyrics = true
                    }

                    playerActionButton("Queue", systemImage: "list.bullet", tint: theme.primary) {
                        showQueue = true
                    }

                    playerActionButton(
                        model.sleepTimerEndTime != nil || model.sleepTimerEndOfSong ? "Timer On" : "Timer",
                        systemImage: "bed.double",
                        tint: model.sleepTimerEndTime != nil || model.sleepTimerEndOfSong ? .pink : theme.primary
                    ) {
                        showSleepTimer = true
                    }
                }

                VStack(spacing: 8) {
                    Slider(
                        value: Binding(
                            get: {
                                if isScrubbing {
                                    return scrubProgress
                                }
                                return model.duration > 0 ? model.currentTime / model.duration : 0
                            },
                            set: { scrubProgress = $0 }
                        ),
                        onEditingChanged: handleScrubbingChanged
                    )
                    .tint(theme.primary)

                    HStack {
                        Text(formatTime(displayedCurrentTime))
                        Spacer()
                        Text(formatTime(model.duration))
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                HStack(spacing: 24) {
                    let isDownloaded = model.isDownloaded(activeSong.id)
                    let downloadProgress = model.downloadProgress[activeSong.id]

                    Button {
                        Task {
                            await model.downloadSong(activeSong)
                        }
                    } label: {
                        if isDownloaded {
                            Image(systemName: "arrow.down.circle.fill")
                                .foregroundStyle(theme.primary)
                        } else if let downloadProgress {
                            ProgressView(value: downloadProgress)
                                .progressViewStyle(.circular)
                                .tint(theme.primary)
                        } else {
                            Image(systemName: "arrow.down.circle")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .disabled(isDownloaded || activeSong.audioURL == nil || downloadProgress != nil)

                    Button(action: model.toggleShuffle) {
                        Image(systemName: "shuffle")
                            .foregroundStyle(model.isShuffleEnabled ? theme.primary : .secondary)
                    }

                    Button(action: model.cycleRepeatMode) {
                        Image(systemName: model.repeatMode == .one ? "repeat.1" : "repeat")
                            .foregroundStyle(model.repeatMode == .off ? .secondary : theme.primary)
                    }

                    Button {
                        model.toggleFavorite(activeSong)
                    } label: {
                        Image(systemName: model.isFavorite(activeSong) ? "heart.fill" : "heart")
                            .foregroundStyle(model.isFavorite(activeSong) ? .pink : .primary)
                    }
                }
                .font(.title2)

                HStack(spacing: 28) {
                    Button(action: model.playPrevious) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 28))
                    }
                    Button(action: model.togglePlayback) {
                        Image(systemName: model.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 70))
                    }
                    Button(action: model.playNext) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 28))
                    }
                }
                .foregroundStyle(.primary)

                if let artCredit = activeSong.artCredit, !artCredit.isEmpty {
                    Text("Art: \(artCredit)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Spacer()
            }
            .padding(.horizontal, 24)
        }
        .onAppear {
            if model.currentSong == nil {
                model.play(song)
            }
            syncScrubProgress()
        }
        .onChange(of: model.currentTime) { _, _ in
            if !isScrubbing {
                syncScrubProgress()
            }
        }
        .onChange(of: model.duration) { _, _ in
            if !isScrubbing {
                syncScrubProgress()
            }
        }
        .sheet(isPresented: $showLyrics) {
            LyricsSheet(model: model)
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showQueue) {
            QueueSheet(model: model, selectedSong: activeSong)
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showSleepTimer) {
            SleepTimerSheet(model: model)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showEqualizer) {
            AudioEffectsSheet(model: model)
                .presentationDetents([.medium, .large])
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite else { return "0:00" }
        let total = Int(seconds.rounded(.down))
        return "\(total / 60):" + String(format: "%02d", total % 60)
    }

    private var displayedCurrentTime: Double {
        guard isScrubbing else { return model.currentTime }
        return model.duration * scrubProgress
    }

    private func handleScrubbingChanged(_ editing: Bool) {
        isScrubbing = editing
        if editing {
            syncScrubProgress()
            return
        }
        model.seek(to: scrubProgress)
    }

    private func syncScrubProgress() {
        scrubProgress = model.duration > 0 ? model.currentTime / model.duration : 0
    }

    private func playerActionButton(
        _ title: String,
        systemImage: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(tint)
    }
}

private struct AudioEffectsSheet: View {
    @ObservedObject var model: AppModel
    @State private var selectedTab = 0

    private let tabs = ["Equalizer", "Bass Boost"]

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 18) {
                Picker("Audio Effects", selection: $selectedTab) {
                    ForEach(Array(tabs.enumerated()), id: \.offset) { index, title in
                        Text(title).tag(index)
                    }
                }
                .pickerStyle(.segmented)

                if !model.audioEffects.isAvailable && !model.audioEffects.bassBoostAvailable {
                    ContentUnavailableView(
                        "Audio Effects Not Available",
                        systemImage: "slider.horizontal.3",
                        description: Text("This iOS build still uses AVPlayer for streaming playback. A real multiband equalizer requires migrating playback to an AVAudioEngine-based pipeline.")
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if selectedTab == 0 {
                    EqualizerTab(model: model)
                } else {
                    BassBoostTab(model: model)
                }
            }
            .padding(20)
            .navigationTitle("Audio Effects")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct EqualizerTab: View {
    @ObservedObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Toggle("Enable Equalizer", isOn: Binding(
                    get: { model.audioEffects.isEnabled },
                    set: model.setAudioEffectsEnabled
                ))
                .disabled(!model.audioEffects.isAvailable)

                if !model.audioEffects.presets.isEmpty {
                    Text("Presets")
                        .font(.headline)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button("Flat") {
                                model.resetEqualizerToFlat()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(model.audioEffects.currentPresetIndex == -1 ? .accentColor : .secondary.opacity(0.3))

                            ForEach(model.audioEffects.presets) { preset in
                                Button(preset.name) {
                                    model.useEqualizerPreset(preset)
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(model.audioEffects.currentPresetIndex == preset.index ? .accentColor : .secondary.opacity(0.3))
                            }
                        }
                    }
                }

                Text("Frequency Bands")
                    .font(.headline)

                ForEach(model.audioEffects.bands) { band in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(formatFrequency(band.centerFrequency))
                            Spacer()
                            Text(formatLevel(band.currentLevel))
                                .foregroundStyle(.secondary)
                        }

                        Slider(
                            value: Binding(
                                get: { Double(band.currentLevel) },
                                set: { model.setEqualizerBandLevel(band.index, level: Int($0.rounded())) }
                            ),
                            in: Double(band.minLevel)...Double(band.maxLevel),
                            step: 50
                        )
                        .disabled(!model.audioEffects.isEnabled || !model.audioEffects.isAvailable)
                    }
                }

                Button("Reset to Flat") {
                    model.resetEqualizerToFlat()
                }
                .buttonStyle(.bordered)
                .disabled(!model.audioEffects.isAvailable)
            }
        }
    }

    private func formatFrequency(_ milliHertz: Int) -> String {
        let hertz = milliHertz / 1000
        return hertz >= 1000 ? "\(hertz / 1000)kHz" : "\(hertz)Hz"
    }

    private func formatLevel(_ milliBels: Int) -> String {
        let db = Double(milliBels) / 100
        return db >= 0 ? String(format: "+%.0fdB", db) : String(format: "%.0fdB", db)
    }
}

private struct BassBoostTab: View {
    @ObservedObject var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Toggle("Enable Bass Boost", isOn: Binding(
                get: { model.audioEffects.bassBoostEnabled },
                set: model.setBassBoostEnabled
            ))
            .disabled(!model.audioEffects.bassBoostAvailable)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Strength")
                    Spacer()
                    Text("\(model.audioEffects.bassBoostStrength / 10)%")
                        .foregroundStyle(.secondary)
                }

                Slider(
                    value: Binding(
                        get: { Double(model.audioEffects.bassBoostStrength) },
                        set: { model.setBassBoostStrength(Int($0.rounded())) }
                    ),
                    in: 0...1000,
                    step: 10
                )
                .disabled(!model.audioEffects.bassBoostEnabled || !model.audioEffects.bassBoostAvailable)

                HStack {
                    Text("Light")
                    Spacer()
                    Text("Heavy")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Spacer()
        }
    }
}

private struct SleepTimerSheet: View {
    @ObservedObject var model: AppModel

    private let presets = [5, 15, 30, 45, 60, 90, 120]

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                if model.sleepTimerEndTime != nil || model.sleepTimerEndOfSong {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Timer Active")
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Text(timerDescription)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(.pink)
                        Button(role: .destructive) {
                            model.cancelSleepTimer()
                        } label: {
                            Text("Cancel Timer")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 20).fill(.ultraThinMaterial.opacity(0.35)))
                }

                Text("Pause playback after")
                    .font(.headline)
                    .foregroundStyle(.primary)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 90))], spacing: 12) {
                    ForEach(presets, id: \.self) { minutes in
                        Button {
                            model.setSleepTimer(minutes: minutes)
                        } label: {
                            Text(label(for: minutes))
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .tint(.accentColor)
                    }
                }

                Button {
                    model.setSleepTimerEndOfSong()
                } label: {
                    Label("End of Current Song", systemImage: "moon.zzz")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.pink)

                Spacer()
            }
            .padding(20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemBackground))
            .navigationTitle("Sleep Timer")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var timerDescription: String {
        if model.sleepTimerEndOfSong {
            return "At the end of the current song"
        }
        return formatRemaining(model.sleepTimerRemaining)
    }

    private func label(for minutes: Int) -> String {
        if minutes < 60 { return "\(minutes) min" }
        let hours = minutes / 60
        let extra = minutes % 60
        return extra == 0 ? "\(hours)h" : "\(hours)h \(extra)m"
    }

    private func formatRemaining(_ interval: TimeInterval) -> String {
        let totalSeconds = max(Int(interval.rounded(.down)), 0)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%d:%02d", minutes, seconds)
    }
}

private struct LyricsSheet: View {
    @ObservedObject var model: AppModel

    var body: some View {
        NavigationStack {
            Group {
                if model.isLoadingLyrics {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if model.lyricLines.isEmpty {
                    EmptyState(icon: "quote.bubble", title: "No lyrics found", message: "Synced or plain lyrics were not available for this track.")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding()
                } else {
                    ScrollViewReader { proxy in
                        List(model.lyricLines) { line in
                            Text(line.text.isEmpty ? "..." : line.text)
                                .font(line.id == model.lyricLines[safe: model.activeLyricIndex]?.id ? .headline : .body)
                                .foregroundStyle(line.id == model.lyricLines[safe: model.activeLyricIndex]?.id ? .primary : .secondary)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .listRowBackground(Color.clear)
                                .id(line.id)
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                        .background(Color(.systemBackground))
                        .onChange(of: model.activeLyricIndex) { _, newValue in
                            guard let newValue, let line = model.lyricLines[safe: newValue] else { return }
                            withAnimation(.easeInOut(duration: 0.25)) {
                                proxy.scrollTo(line.id, anchor: .center)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Lyrics")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct QueueSheet: View {
    @ObservedObject var model: AppModel
    let selectedSong: Song

    var body: some View {
        NavigationStack {
            List {
                ForEach(model.queueSongs) { song in
                    Button {
                        model.playQueueSong(song)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(song.title)
                                    .foregroundStyle(.primary)
                                Text(song.artist)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if song.id == selectedSong.id {
                                Image(systemName: "speaker.wave.2.fill")
                                    .foregroundStyle(.cyan)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(Color.clear)
                }
                .onMove(perform: model.moveQueueSongs)
                .onDelete(perform: model.removeQueueSongs)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Color(.systemBackground))
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    EditButton()
                }
            }
        }
    }
}

private struct BannerArtwork: View {
    let imageURL: URL?
    @State private var loadedImage: UIImage?

    var body: some View {
        GeometryReader { geometry in
            Group {
                if let loadedImage {
                    artworkImage(for: loadedImage, in: geometry.size)
                } else {
                    placeholder
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .task(id: imageURL) {
            await loadImage()
        }
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(colors: [.cyan.opacity(0.6), .pink.opacity(0.4)], startPoint: .topLeading, endPoint: .bottomTrailing)
            Image(systemName: "music.note")
                .font(.system(size: 34, weight: .medium))
                .foregroundStyle(.primary.opacity(0.85))
        }
    }

    private func imagePadding(for size: CGSize) -> CGFloat {
        guard size.width > 0, size.height > 0 else { return 0 }

        let ratio = size.width / size.height
        switch ratio {
        case ..<0.85:
            return 18
        case 1.15...:
            return 20
        default:
            return 0
        }
    }

    @ViewBuilder
    private func artworkImage(for image: UIImage, in size: CGSize) -> some View {
        let ratio = image.size.width / max(image.size.height, 1)
        let isRoughlySquare = (0.9...1.1).contains(ratio)

        if isRoughlySquare {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: size.width, height: size.height)
                .clipped()
        } else {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .padding(imagePadding(for: image.size))
        }
    }

    @MainActor
    private func loadImage() async {
        guard let imageURL else {
            loadedImage = nil
            return
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: imageURL)
            loadedImage = UIImage(data: data)
        } catch {
            loadedImage = nil
        }
    }
}

private struct AppTheme {
    let primary: Color
    let secondary: Color
    let backgroundTop: Color
    let backgroundBottom: Color

    static func forSinger(_ singer: Singer?, colorScheme: ColorScheme = .dark) -> AppTheme {
        let isDark = colorScheme == .dark

        switch singer {
        case .evil:
            return AppTheme(
                primary: Color(red: 0.91, green: 0.12, blue: 0.55),
                secondary: Color(red: 0.62, green: 0.15, blue: 0.69),
                backgroundTop: isDark ? Color(red: 0.11, green: 0.05, blue: 0.09) : Color(red: 0.98, green: 0.93, blue: 0.96),
                backgroundBottom: isDark ? Color(red: 0.23, green: 0.08, blue: 0.15) : Color(red: 0.95, green: 0.87, blue: 0.91)
            )
        case .duet:
            return AppTheme(
                primary: Color(red: 0.61, green: 0.37, blue: 0.83),
                secondary: Color(red: 0.70, green: 0.53, blue: 0.91),
                backgroundTop: isDark ? Color(red: 0.07, green: 0.05, blue: 0.12) : Color(red: 0.95, green: 0.93, blue: 0.99),
                backgroundBottom: isDark ? Color(red: 0.17, green: 0.12, blue: 0.24) : Color(red: 0.89, green: 0.86, blue: 0.97)
            )
        default:
            return AppTheme(
                primary: Color(red: 0.00, green: 0.85, blue: 1.00),
                secondary: Color(red: 0.00, green: 0.60, blue: 0.80),
                backgroundTop: isDark ? Color(red: 0.07, green: 0.08, blue: 0.11) : Color(red: 0.92, green: 0.97, blue: 1.00),
                backgroundBottom: isDark ? Color(red: 0.12, green: 0.14, blue: 0.19) : Color(red: 0.85, green: 0.93, blue: 0.98)
            )
        }
    }
}

#Preview("Home Dark") {
    ContentView(model: .previewModel(selectedTab: .home))
        .preferredColorScheme(.dark)
}

#Preview("Library Light") {
    ContentView(model: .previewModel(selectedTab: .library))
        .preferredColorScheme(.light)
}

#Preview("Radio Dark") {
    ContentView(model: .previewModel(selectedTab: .radio))
        .preferredColorScheme(.dark)
}

#Preview("Setup") {
    ContentView(model: AppModel())
        .preferredColorScheme(.dark)
}

private extension Array {
    subscript(safe index: Int?) -> Element? {
        guard let index, indices.contains(index) else { return nil }
        return self[index]
    }
}

private extension ProcessInfo {
    var isRunningForPreviews: Bool {
        environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1"
    }
}
