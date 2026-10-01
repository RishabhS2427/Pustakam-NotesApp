
import Foundation
import Photos          // 🔧 14-Jul-2026: save images/videos to the Photos gallery
import UIKit           // 🔧 14-Jul-2026: UIAlertController action sheet for the save-options chooser
import shared          // 🔧 14-Jul-2026: NoteContentModel / ContentType for the save dispatcher


//Creating A file in specific path
func createFilepath(in folderName: String, fileName: String, contents: Data) -> URL? {
    guard let folderURL = createFolder(named: folderName) else { return nil }
    let fileURL = folderURL.appendingPathComponent(fileName)

    FileManager.default.createFile(atPath: fileURL.path, contents: contents, attributes: nil)
    return fileURL
}

//Create a New Folder in Documents Directory

func createFolder(named folderName: String) -> URL? {
    let documentsDirectory = getDocumentsDirectory()
    let folderURL = documentsDirectory.appendingPathComponent(folderName)

    if !FileManager.default.fileExists(atPath: folderURL.path) {
        do {
            try FileManager.default.createDirectory(at: folderURL, withIntermediateDirectories: true, attributes: nil)
            print("Folder created at: \(folderURL.path)")
        } catch {
            print("Error creating folder: \(error)")
            return nil
        }
    }

    return folderURL
}


//Get a Specific Directory Path (e.g., Documents)

func getDocumentsDirectory() -> URL {
    FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
}


func getATempFilePath(extension ext : String) -> URL{
    FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ext)
}

//save data into location
func saveData(url : URL, data : Data){
    do {
        try data.write(to: url)
    }catch {
        print("❌ Failed to save: \(error)")
    }
}

//move data into location
func moveData(from url  : URL , to destinationUrl  : URL){
    do{
        try FileManager.default.copyItem(at: url, to: destinationUrl)
    }catch {
        print("❌ Failed to move: \(error)")
    }
}
// save media file
func saveImageFile(data content : Data, in folder : String,to filePath : String ,) -> String {
    
    guard let  fileSavingPath = createFilepath(in: folder, fileName: filePath, contents: content) else {return ""}
    saveData(url: fileSavingPath , data: content)
    return fileSavingPath.path
}

func saveVideoFile(in folder : String,to filePath : String, url : URL) -> String{
     var localpath  = ""
    if let videoData = try? Data(contentsOf: url) {
       let   savedURL = createFilepath(in: folder, fileName: filePath, contents: videoData)
        print("Saved at: \(String(describing: savedURL))")
    } else {
        print("Failed to read video data.")
    }
    
    return localpath
}
// 🔧 14-Jul-2026: NEW — delete a media file from disk when its note-content block is removed
//   (Android parity: NoteEditorViewModel.removeContent deletes the local file too).
//   Usage: deleteFile(filePath: media.localPath) — safe to call if the file no longer exists.
func deleteFile(filePath: String) {
    guard !filePath.isEmpty, FileManager.default.fileExists(atPath: filePath) else { return }
    do {
        try FileManager.default.removeItem(atPath: filePath)
    } catch {
        print("❌ Failed to delete file: \(error)")
    }
}

func deleteFilesLater(_ paths: [String]) {
    guard !paths.isEmpty else { return }
    DispatchQueue.global(qos: .utility).async {
        for path in paths {
            deleteFile(filePath: LocalFilePathResolver_iosKt.resolveLocalFilePath(path: path) ?? path)
        }
    }
}

func saveMediaToDevice(media: NoteContentModel.MediaContent) {
    let path = media.getMediaUrl()
    guard !path.isEmpty, FileManager.default.fileExists(atPath: path) else {
        print("❌ Save failed — source file missing at \(path)")
        return
    }
    let fileURL = URL(fileURLWithPath: path)
    switch media.type {
    case .image, .video:

        presentSaveOptions(fileURL: fileURL, isVideo: media.type == .video)
    default:
        presentDocumentExporter(fileURL: fileURL)
    }
}

func presentSaveOptions(fileURL: URL, isVideo: Bool) {
    guard let top = topMostViewController() else {
        print("❌ No view controller available to present the save options")
        return
    }
    let sheet = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
    sheet.addAction(UIAlertAction(title: "Save to Gallery", style: .default) { _ in
        saveMediaToGallery(fileURL: fileURL, isVideo: isVideo)
    })
    sheet.addAction(UIAlertAction(title: "Save to location as file", style: .default) { _ in
        presentDocumentExporter(fileURL: fileURL)
    })
    sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    // iPad requires an anchor for action sheets.
    if let popover = sheet.popoverPresentationController {
        popover.sourceView = top.view
        popover.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.maxY, width: 0, height: 0)
        popover.permittedArrowDirections = []
    }
    top.present(sheet, animated: true)
}

// 🔧 14-Jul-2026: Save an image or video file into the Photos library.
//   Requests add-only access (NSPhotoLibraryAddUsageDescription is declared in Info.plist).
//   Usage:  saveMediaToGallery(fileURL: url, isVideo: true)
func saveMediaToGallery(fileURL: URL, isVideo: Bool) {
    PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
        guard status == .authorized || status == .limited else {
            print("❌ Photo library permission denied")
            return
        }
        PHPhotoLibrary.shared().performChanges({
            if isVideo {
                _ = PHAssetCreationRequest.creationRequestForAssetFromVideo(atFileURL: fileURL)
            } else {
                _ = PHAssetCreationRequest.creationRequestForAssetFromImage(atFileURL: fileURL)
            }
        }) { success, error in
            if success {
                print("✅ Saved to Photos gallery")
            } else {
                print("❌ Save to Photos failed: \(String(describing: error))")
            }
        }
    }
}

// for large files
func copyFile(to folderName: String, fileName: String, from sourceURL: URL) -> URL? {
    guard let folderURL = createFolder(named: folderName) else { return nil }
    let destinationURL = folderURL.appendingPathComponent(fileName)

    do {
        try FileManager.default.copyItem(at: sourceURL, to: destinationURL)
        return destinationURL
    } catch {
        print("Failed to copy file: \(error)")
        return nil
    }
}

// 🔧 15-Jul-2026 iOS parity (Phase 2.3): lazy thumbnails — a small JPEG (max ~512px, q0.7) written
//   under Documents/thumbnails/, so list cards never decode the full image or open a video stream.
//   Android counterpart: fileUtils/FileOps.kt generateThumbnail(). Returns the thumbnail's path,
//   or nil on failure (callers keep their current fallback behavior).
//   • IMAGE/GIF: UIImage downscale via UIGraphicsImageRenderer
//   • VIDEO:     frame at ~1s via AVAssetImageGenerator (skips black lead-ins), fallback frame 0
//   Usage: generateThumbnail(sourcePath: media.localPath!, type: media.type)
import AVFoundation

// 🔧 30-Jul-2026 Phase 4 — size and quality come from the SHARED ThumbnailPolicy so iOS and Android
//   cannot drift. Accessor functions, not the `const val`s: a const inside a Kotlin object has no
//   guaranteed ObjC/Swift export shape. The DECODING below stays native (UIImage / AVAssetImageGenerator).
private let thumbnailMaxDimension = CGFloat(ThumbnailPolicy.shared.maxDimensionPx())
private let thumbnailJpegQuality = CGFloat(ThumbnailPolicy.shared.jpegQuality()) / 100.0

func generateThumbnail(sourcePath: String, type: ContentType) -> String? {
    let sourceURL = URL(fileURLWithPath: sourcePath)
    guard FileManager.default.fileExists(atPath: sourcePath) else { return nil }

    var image: UIImage?
    if type == ContentType.image || type == ContentType.gif {
        image = UIImage(contentsOfFile: sourcePath)
    } else if type == ContentType.video {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: sourceURL))
        generator.appliesPreferredTrackTransform = true
        // 🔧 30-Jul-2026 Phase 4 — frame offset from the shared ThumbnailPolicy (µs → seconds)
        let frameSeconds = Double(ThumbnailPolicy.shared.videoFrameMicros()) / 1_000_000.0
        if let cg = try? generator.copyCGImage(at: CMTime(seconds: frameSeconds, preferredTimescale: 600), actualTime: nil) {
            image = UIImage(cgImage: cg)
        } else if let cg = try? generator.copyCGImage(at: .zero, actualTime: nil) {
            image = UIImage(cgImage: cg)
        }
    }
    guard let source = image else { return nil }

    // proportional downscale so the longest side is thumbnailMaxDimension
    let longest = max(source.size.width, source.size.height)
    let scale = longest > thumbnailMaxDimension ? thumbnailMaxDimension / longest : 1
    let targetSize = CGSize(width: max(source.size.width * scale, 1),
                            height: max(source.size.height * scale, 1))
    let scaled = UIGraphicsImageRenderer(size: targetSize).image { _ in
        source.draw(in: CGRect(origin: .zero, size: targetSize))
    }
    guard let data = scaled.jpegData(compressionQuality: thumbnailJpegQuality),
          let folderURL = createFolder(named: "thumbnails") else { return nil }
    let thumbURL = folderURL.appendingPathComponent(
        sourceURL.deletingPathExtension().lastPathComponent + "_thumb.jpg")
    do {
        try data.write(to: thumbURL)
        return thumbURL.path
    } catch {
        print("Failed to write thumbnail: \(error)")
        return nil
    }
}
