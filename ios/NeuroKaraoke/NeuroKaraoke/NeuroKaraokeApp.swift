//
//  NeuroKaraokeApp.swift
//  NeuroKaraoke
//
//  Created by cosmii02 on 27.03.2026.
//

import SwiftUI

@main
struct NeuroKaraokeApp: App {
    var body: some Scene {
        WindowGroup {
            AppBootstrapView()
        }
    }
}

private struct AppBootstrapView: View {
    @State private var model: AppModel?
    @State private var pendingOpenURL: URL?

    var body: some View {
        Group {
            if let model {
                ContentView(model: model)
            } else {
                SetupView(
                    progress: SetupProgress(
                        fraction: 0,
                        status: "Preparing…",
                        detail: "Starting interface"
                    ),
                    errorMessage: nil
                )
            }
        }
        .task {
            guard model == nil else { return }
            await Task.yield()
            let appModel = AppModel()
            model = appModel

            if let pendingOpenURL {
                appModel.handleOpenURL(pendingOpenURL)
                self.pendingOpenURL = nil
            }
        }
        .onOpenURL { url in
            if let model {
                model.handleOpenURL(url)
            } else {
                pendingOpenURL = url
            }
        }
    }
}
