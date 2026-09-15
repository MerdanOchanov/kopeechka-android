import SwiftUI
import Shared

// Весь интерфейс рисует общий модуль на Compose Multiplatform —
// Swift здесь только заводит окно.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct KopeechkaApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all)
        }
    }
}
