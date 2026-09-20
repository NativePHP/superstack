import AVFoundation
import PhotosUI
import UIKit
import Vision

enum ScannerFunctions {

    static let codeScannedEvent = "Sandip\\Scanner\\Native\\Events\\Scanner\\CodeScanned"
    static let cancelledEvent = "Sandip\\Scanner\\Native\\Events\\Scanner\\Cancelled"

    static weak var activeController: ScannerViewController?

    static let validFormats: Set<String> = ["qr", "ean13", "ean8", "code128", "code39", "upca", "upce"]

    private static func metadataObjectTypes(for names: [String]) -> [AVMetadataObject.ObjectType] {
        if names.contains("all") {
            return [.qr, .ean13, .ean8, .code128, .code39, .upce, .code93, .pdf417, .aztec, .dataMatrix, .interleaved2of5, .itf14, .codabar]
        }

        var types = Set<AVMetadataObject.ObjectType>()
        for name in names {
            switch name {
            case "qr": types.insert(.qr)
            case "ean13": types.insert(.ean13)
            case "ean8": types.insert(.ean8)
            case "code128": types.insert(.code128)
            case "code39": types.insert(.code39)
            case "upce": types.insert(.upce)
            case "upca": types.insert(.ean13)
            default: break
            }
        }
        return Array(types)
    }

    static func barcodeSymbologies(for names: [String]) -> [VNBarcodeSymbology] {
        if names.contains("all") {
            return [.qr, .ean13, .ean8, .code128, .code39, .upce, .code93, .pdf417, .aztec, .dataMatrix, .itf14, .codabar]
        }

        var symbologies = Set<VNBarcodeSymbology>()
        for name in names {
            switch name {
            case "qr": symbologies.insert(.qr)
            case "ean13": symbologies.insert(.ean13)
            case "ean8": symbologies.insert(.ean8)
            case "code128": symbologies.insert(.code128)
            case "code39": symbologies.insert(.code39)
            case "upce": symbologies.insert(.upce)
            case "upca": symbologies.insert(.ean13)
            default: break
            }
        }
        return symbologies.isEmpty ? [.qr] : Array(symbologies)
    }

    class Scan: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let prompt = parameters["prompt"] as? String ?? ""
            let continuous = parameters["continuous"] as? Bool ?? false
            let allowGallery = parameters["allowGallery"] as? Bool ?? true
            let id = parameters["id"] as? String
            let requestedFormats = (parameters["formats"] as? [String])?.filter { !$0.isEmpty } ?? ["qr"]
            let haptics = parameters["haptics"] as? Bool ?? true
            let zoom = (parameters["zoom"] as? NSNumber)?.doubleValue ?? 1.0
            let maxZoom = (parameters["maxZoom"] as? NSNumber)?.doubleValue ?? 3.0
            let zoomControl = parameters["zoomControl"] as? Bool ?? true
            let focusOnTap = parameters["focusOnTap"] as? Bool ?? true
            let timeoutSeconds = (parameters["timeout"] as? NSNumber)?.intValue ?? 0

            let unknown = requestedFormats.filter { $0 != "all" && !ScannerFunctions.validFormats.contains($0) }
            if !unknown.isEmpty {
                return BridgeResponse.error(
                    code: "INVALID_FORMAT",
                    message: "Unknown barcode format(s): \(unknown.joined(separator: ", ")). Valid formats are: \(ScannerFunctions.validFormats.sorted().joined(separator: ", ")), all."
                )
            }

            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                DispatchQueue.main.async {
                    ScannerFunctions.present(
                        prompt: prompt,
                        continuous: continuous,
                        allowGallery: allowGallery,
                        formats: requestedFormats,
                        haptics: haptics,
                        zoom: zoom,
                        maxZoom: maxZoom,
                        zoomControl: zoomControl,
                        focusOnTap: focusOnTap,
                        timeoutSeconds: timeoutSeconds,
                        id: id
                    )
                }
                return BridgeResponse.success(data: ["started": true])

            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async {
                        LaravelBridge.shared.send?(ScannerFunctions.cancelledEvent, [
                            "reason": granted ? "permission_required" : "permission_denied",
                            "id": id,
                        ])
                    }
                }

                return BridgeResponse.success(data: ["permissionRequested": true])

            case .denied, .restricted:
                return BridgeResponse.error(
                    code: "PERMISSION_DENIED",
                    message: "Camera access is denied. Enable it in Settings to use the scanner."
                )

            @unknown default:
                return BridgeResponse.error(code: "PERMISSION_DENIED", message: "Camera access is unavailable.")
            }
        }
    }

    class Stop: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let id = parameters["id"] as? String

            guard let controller = ScannerFunctions.activeController,
                  id == nil || controller.sessionId == id else {
                return BridgeResponse.success(data: ["stopped": false])
            }

            DispatchQueue.main.async {
                controller.finish(cancelled: true, reason: "stopped_by_app")
            }

            return BridgeResponse.success(data: ["stopped": true])
        }
    }

    private static func present(prompt: String, continuous: Bool, allowGallery: Bool, formats: [String], haptics: Bool, zoom: Double, maxZoom: Double, zoomControl: Bool, focusOnTap: Bool, timeoutSeconds: Int, id: String?) {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })?.rootViewController

        guard let presenter = rootViewController else {
            LaravelBridge.shared.send?(cancelledEvent, [
                "reason": "no_root_view_controller",
                "id": id,
            ])
            return
        }

        activeController?.finish(cancelled: true, reason: nil)

        let types = metadataObjectTypes(for: formats)
        let controller = ScannerViewController(
            prompt: prompt,
            continuous: continuous,
            allowGallery: allowGallery,
            formats: formats,
            types: types,
            haptics: haptics,
            zoom: zoom,
            maxZoom: maxZoom,
            zoomControl: zoomControl,
            focusOnTap: focusOnTap,
            timeoutSeconds: timeoutSeconds,
            id: id
        )
        controller.modalPresentationStyle = .fullScreen
        activeController = controller
        presenter.present(controller, animated: true)
    }
}

final class ViewfinderOverlayView: UIView {

    private static let successColor = UIColor(red: 0.20, green: 0.83, blue: 0.60, alpha: 1)
    private static let cornerRadius: CGFloat = 24

    private let borderLayer = CAShapeLayer()
    private let pulseLayer = CAShapeLayer()

    private(set) var scanWindowFrame: CGRect = .zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false

        borderLayer.fillColor = UIColor.clear.cgColor
        borderLayer.strokeColor = UIColor.white.cgColor
        borderLayer.lineWidth = 3
        layer.addSublayer(borderLayer)

        pulseLayer.fillColor = UIColor.clear.cgColor
        pulseLayer.strokeColor = ViewfinderOverlayView.successColor.cgColor
        pulseLayer.lineWidth = 4
        pulseLayer.opacity = 0
        layer.addSublayer(pulseLayer)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        let side = min(bounds.width, bounds.height) * 0.68
        let rect = CGRect(
            x: (bounds.width - side) / 2,
            y: bounds.height * 0.26,
            width: side,
            height: side
        )
        scanWindowFrame = rect
        borderLayer.path = UIBezierPath(roundedRect: rect, cornerRadius: ViewfinderOverlayView.cornerRadius).cgPath
    }

    func playSuccessPulse(duration: TimeInterval, targetRect: CGRect?) {
        let target = targetRect ?? scanWindowFrame
        guard target.width > 0, target.height > 0 else { return }

        borderLayer.opacity = 0

        let radius = min(target.width, target.height) * 0.12
        let inflated = target.insetBy(dx: -target.width * 0.15, dy: -target.height * 0.15)
        let startPath = UIBezierPath(roundedRect: inflated, cornerRadius: radius * 1.3).cgPath
        let exactPath = UIBezierPath(roundedRect: target, cornerRadius: radius).cgPath

        pulseLayer.removeAllAnimations()
        pulseLayer.path = exactPath
        pulseLayer.opacity = 0

        let pathAnimation = CAKeyframeAnimation(keyPath: "path")
        pathAnimation.values = [startPath, exactPath, exactPath, exactPath]
        pathAnimation.keyTimes = [0, 0.25, 0.7, 1.0]
        pathAnimation.timingFunctions = [
            CAMediaTimingFunction(name: .easeOut),
            CAMediaTimingFunction(name: .linear),
            CAMediaTimingFunction(name: .linear),
        ]

        let fadeAnimation = CAKeyframeAnimation(keyPath: "opacity")
        fadeAnimation.values = [1.0, 1.0, 1.0, 0.0]
        fadeAnimation.keyTimes = [0, 0.25, 0.7, 1.0]

        let group = CAAnimationGroup()
        group.animations = [pathAnimation, fadeAnimation]
        group.duration = duration

        pulseLayer.add(group, forKey: "successPulse")

        DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
            self?.borderLayer.opacity = 1
        }
    }
}

final class FocusIndicatorView: UIView {

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func animateFocus(at point: CGPoint) {
        let size: CGFloat = 72
        let ring = UIView(frame: CGRect(x: point.x - size / 2, y: point.y - size / 2, width: size, height: size))
        ring.layer.borderColor = UIColor.white.cgColor
        ring.layer.borderWidth = 1.5
        ring.layer.cornerRadius = size / 2
        ring.alpha = 0
        ring.transform = CGAffineTransform(scaleX: 1.3, y: 1.3)
        addSubview(ring)

        UIView.animate(withDuration: 0.18, animations: {
            ring.alpha = 1
            ring.transform = .identity
        }) { _ in
            UIView.animate(withDuration: 0.3, delay: 0.4, options: [], animations: {
                ring.alpha = 0
            }) { _ in
                ring.removeFromSuperview()
            }
        }
    }
}

final class CameraZoomSliderView: UIView {

    var minValue: CGFloat = 1.0
    var maxValue: CGFloat = 3.0
    var onValueChanged: ((CGFloat) -> Void)?

    var value: CGFloat = 1.0 {
        didSet {
            let clamped = min(max(value, minValue), maxValue)
            if clamped != value {
                value = clamped
                return
            }
            setNeedsDisplay()
        }
    }

    private let thumbRadius: CGFloat = 9
    private let trackColor = UIColor.white.withAlphaComponent(0.3)
    private let progressColor = UIColor.white
    private let thumbColor = UIColor.white
    private let thumbShadowColor = UIColor.black.withAlphaComponent(0.25)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isUserInteractionEnabled = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private var trackLeft: CGFloat { thumbRadius }
    private var trackRight: CGFloat { bounds.width - thumbRadius }

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        let cy = bounds.height / 2
        let left = trackLeft
        let right = trackRight
        guard right > left else { return }

        let range = max(maxValue - minValue, 0.001)
        let ratio = (value - minValue) / range
        let thumbX = left + (right - left) * ratio

        ctx.setLineCap(.round)
        ctx.setLineWidth(2)

        ctx.setStrokeColor(trackColor.cgColor)
        ctx.move(to: CGPoint(x: left, y: cy))
        ctx.addLine(to: CGPoint(x: right, y: cy))
        ctx.strokePath()

        ctx.setStrokeColor(progressColor.cgColor)
        ctx.move(to: CGPoint(x: left, y: cy))
        ctx.addLine(to: CGPoint(x: thumbX, y: cy))
        ctx.strokePath()

        ctx.setFillColor(thumbShadowColor.cgColor)
        ctx.fillEllipse(in: CGRect(x: thumbX - thumbRadius, y: cy - thumbRadius + 1, width: thumbRadius * 2, height: thumbRadius * 2))

        ctx.setFillColor(thumbColor.cgColor)
        ctx.fillEllipse(in: CGRect(x: thumbX - thumbRadius, y: cy - thumbRadius, width: thumbRadius * 2, height: thumbRadius * 2))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        updateValue(fromTouchX: touch.location(in: self).x)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        updateValue(fromTouchX: touch.location(in: self).x)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        updateValue(fromTouchX: touch.location(in: self).x)
    }

    private func updateValue(fromTouchX x: CGFloat) {
        let left = trackLeft
        let right = trackRight
        guard right > left else { return }
        let ratio = min(max((x - left) / (right - left), 0), 1)
        let newValue = minValue + (maxValue - minValue) * ratio
        value = newValue
        onValueChanged?(newValue)
    }
}

final class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {

    private let prompt: String
    private let continuous: Bool
    private let allowGallery: Bool
    private let requestedFormats: [String]
    private let types: [AVMetadataObject.ObjectType]
    private let hapticsEnabled: Bool
    private let initialZoom: Double
    private let maxZoomConfigured: Double
    private let zoomControlEnabled: Bool
    private let focusOnTap: Bool
    private let timeoutSeconds: Int
    let sessionId: String?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var captureDevice: AVCaptureDevice?
    private var finished = false
    private var matched = false
    private var torchOn = false
    private let torchButton = UIButton(type: .system)
    private let galleryButton = UIButton(type: .system)
    private var promptLabel: UILabel?
    private let viewfinderOverlay = ViewfinderOverlayView()
    private let focusIndicatorView = FocusIndicatorView()
    private let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
    private var timeoutWorkItem: DispatchWorkItem?

    private let zoomSliderView = CameraZoomSliderView()
    private let zoomValueLabel = UILabel()
    private var zoomLowerBound: CGFloat = 1.0
    private var zoomUpperBound: CGFloat = 3.0
    private var currentZoom: CGFloat = 1.0

    private var lastValue: String?
    private var lastFiredAt: TimeInterval = 0
    private let repeatDebounceSeconds: TimeInterval = 2.0
    private static let successPulseDuration: TimeInterval = 1.0

    init(prompt: String, continuous: Bool, allowGallery: Bool, formats: [String], types: [AVMetadataObject.ObjectType], haptics: Bool, zoom: Double, maxZoom: Double, zoomControl: Bool, focusOnTap: Bool, timeoutSeconds: Int, id: String?) {
        self.prompt = prompt
        self.continuous = continuous
        self.allowGallery = allowGallery
        self.requestedFormats = formats
        self.types = types
        self.hapticsEnabled = haptics
        self.initialZoom = zoom
        self.maxZoomConfigured = maxZoom
        self.zoomControlEnabled = zoomControl
        self.focusOnTap = focusOnTap
        self.timeoutSeconds = timeoutSeconds
        self.sessionId = id
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setUpCamera()
        setUpOverlay()

        if hapticsEnabled {
            impactFeedback.prepare()
        }

        if timeoutSeconds > 0 {
            let workItem = DispatchWorkItem { [weak self] in
                self?.finish(cancelled: true, reason: "timeout")
            }
            timeoutWorkItem = workItem
            DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(timeoutSeconds), execute: workItem)
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        guard !session.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async { [session] in
            session.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        guard session.isRunning else { return }
        session.stopRunning()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds

        viewfinderOverlay.frame = view.bounds
        viewfinderOverlay.layoutIfNeeded()

        let square = viewfinderOverlay.scanWindowFrame

        if allowGallery {
            var size = galleryButton.bounds.size
            if size == .zero {
                galleryButton.sizeToFit()
                size = galleryButton.bounds.size
            }
            galleryButton.frame = CGRect(
                x: (view.bounds.width - size.width) / 2,
                y: square.maxY + 28,
                width: size.width,
                height: size.height
            )
        }

        if zoomControlEnabled {
            let sliderWidth: CGFloat = 200
            let sliderHeight: CGFloat = 32
            let labelHeight: CGFloat = 18
            let labelGap: CGFloat = 4

            let bottomLimit: CGFloat
            if let promptLabel = promptLabel, !prompt.isEmpty {
                bottomLimit = promptLabel.frame.minY - 14
            } else {
                bottomLimit = view.bounds.height - view.safeAreaInsets.bottom - 28
            }

            zoomSliderView.frame = CGRect(
                x: (view.bounds.width - sliderWidth) / 2,
                y: bottomLimit - sliderHeight,
                width: sliderWidth,
                height: sliderHeight
            )
            zoomValueLabel.frame = CGRect(
                x: (view.bounds.width - sliderWidth) / 2,
                y: zoomSliderView.frame.minY - labelGap - labelHeight,
                width: sliderWidth,
                height: labelHeight
            )
        }
    }

    private func setUpCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            finish(cancelled: true, reason: "camera_error")
            return
        }
        session.addInput(input)
        captureDevice = device

        if zoomControlEnabled || initialZoom != 1.0 {
            let deviceMin = max(device.minAvailableVideoZoomFactor, 1.0)
            let deviceMax = device.maxAvailableVideoZoomFactor
            zoomLowerBound = deviceMin
            zoomUpperBound = max(min(CGFloat(maxZoomConfigured), deviceMax), deviceMin)

            let clampedZoom = min(max(CGFloat(initialZoom), zoomLowerBound), zoomUpperBound)
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = clampedZoom
                device.unlockForConfiguration()
            } catch {
            }
            currentZoom = clampedZoom
        }

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            finish(cancelled: true, reason: "camera_error")
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = types.filter { output.availableMetadataObjectTypes.contains($0) }

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.insertSublayer(layer, at: 0)
        previewLayer = layer
    }

    private func setUpOverlay() {
        viewfinderOverlay.frame = view.bounds
        viewfinderOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        viewfinderOverlay.isUserInteractionEnabled = false
        view.addSubview(viewfinderOverlay)

        focusIndicatorView.frame = view.bounds
        focusIndicatorView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        focusIndicatorView.isUserInteractionEnabled = false
        view.addSubview(focusIndicatorView)

        if focusOnTap {
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleFocusTap(_:)))
            tapGesture.delegate = self
            view.addGestureRecognizer(tapGesture)
        }

        let titleLabel = UILabel()
        titleLabel.text = "Scan Code"
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(titleLabel)

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark")?.withRenderingMode(.alwaysTemplate), for: .normal)
        styleIconButton(closeButton)
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        view.addSubview(closeButton)

        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            closeButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            closeButton.widthAnchor.constraint(equalToConstant: 40),
            closeButton.heightAnchor.constraint(equalToConstant: 40),

            titleLabel.centerYAnchor.constraint(equalTo: closeButton.centerYAnchor),
            titleLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        ])

        if allowGallery {
            galleryButton.setTitle("Choose from Gallery", for: .normal)
            galleryButton.setTitleColor(.white, for: .normal)
            galleryButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
            galleryButton.backgroundColor = UIColor.white.withAlphaComponent(0.14)
            galleryButton.layer.borderWidth = 1.5
            galleryButton.layer.borderColor = UIColor.white.cgColor
            galleryButton.layer.cornerRadius = 22
            galleryButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 24, bottom: 12, right: 24)
            galleryButton.addTarget(self, action: #selector(galleryTapped), for: .touchUpInside)
            view.addSubview(galleryButton)
            galleryButton.sizeToFit()
        }

        if zoomControlEnabled {
            zoomValueLabel.text = String(format: "%.1fx", currentZoom)
            zoomValueLabel.textColor = .white
            zoomValueLabel.font = .systemFont(ofSize: 13, weight: .semibold)
            zoomValueLabel.textAlignment = .center
            view.addSubview(zoomValueLabel)

            zoomSliderView.minValue = zoomLowerBound
            zoomSliderView.maxValue = zoomUpperBound
            zoomSliderView.value = currentZoom
            zoomSliderView.onValueChanged = { [weak self] newValue in
                self?.applyZoomFromSlider(newValue)
            }
            view.addSubview(zoomSliderView)
        }

        if !prompt.isEmpty {
            let promptLabel = UILabel()
            promptLabel.text = prompt
            promptLabel.textColor = .white
            promptLabel.textAlignment = .center
            promptLabel.numberOfLines = 0
            promptLabel.font = .systemFont(ofSize: 14, weight: .regular)
            promptLabel.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(promptLabel)
            self.promptLabel = promptLabel

            NSLayoutConstraint.activate([
                promptLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 32),
                promptLabel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -32),
                promptLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32),
            ])
        }

        guard let device = captureDevice, device.hasTorch else { return }

        torchButton.setImage(UIImage(systemName: "bolt.slash.fill")?.withRenderingMode(.alwaysTemplate), for: .normal)
        styleIconButton(torchButton)
        torchButton.addTarget(self, action: #selector(torchTapped), for: .touchUpInside)
        view.addSubview(torchButton)

        NSLayoutConstraint.activate([
            torchButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            torchButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            torchButton.widthAnchor.constraint(equalToConstant: 40),
            torchButton.heightAnchor.constraint(equalToConstant: 40),
        ])
    }

    private func styleIconButton(_ button: UIButton) {
        button.backgroundColor = .white
        button.tintColor = .black
        button.translatesAutoresizingMaskIntoConstraints = false
        button.layer.cornerRadius = 20
        button.clipsToBounds = true
        button.setPreferredSymbolConfiguration(
            UIImage.SymbolConfiguration(pointSize: 16, weight: .semibold),
            forImageIn: .normal
        )
    }

    @objc private func closeTapped() {
        finish(cancelled: true, reason: "user_cancelled")
    }

    @objc private func galleryTapped() {
        guard allowGallery else { return }

        var configuration = PHPickerConfiguration()
        configuration.filter = .images
        configuration.selectionLimit = 1

        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        present(picker, animated: true)
    }

    @objc private func handleFocusTap(_ gesture: UITapGestureRecognizer) {
        guard let device = captureDevice, let layer = previewLayer else { return }

        let location = gesture.location(in: view)
        let devicePoint = layer.captureDevicePointConverted(fromLayerPoint: location)

        do {
            try device.lockForConfiguration()
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = devicePoint
                device.focusMode = .autoFocus
            }
            if device.isExposurePointOfInterestSupported {
                device.exposurePointOfInterest = devicePoint
                device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()
            focusIndicatorView.animateFocus(at: location)
        } catch {
        }
    }

    private func applyZoomFromSlider(_ value: CGFloat) {
        guard let device = captureDevice else { return }

        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = value
            device.unlockForConfiguration()
            currentZoom = value
            zoomValueLabel.text = String(format: "%.1fx", value)
        } catch {
        }
    }

    @objc private func torchTapped() {
        guard let device = captureDevice, device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            torchOn.toggle()
            device.torchMode = torchOn ? .on : .off
            device.unlockForConfiguration()
            let symbolName = torchOn ? "bolt.fill" : "bolt.slash.fill"
            torchButton.setImage(UIImage(systemName: symbolName)?.withRenderingMode(.alwaysTemplate), for: .normal)
        } catch {
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue else {
            return
        }

        let format = ScannerViewController.formatName(for: object.type, value: value, requested: requestedFormats)

        let codeRect = previewLayer?.transformedMetadataObject(for: object)?.bounds

        if !continuous {
            guard !matched else { return }
            matched = true

            triggerHapticFeedback()
            viewfinderOverlay.playSuccessPulse(duration: ScannerViewController.successPulseDuration, targetRect: codeRect)
            DispatchQueue.main.asyncAfter(deadline: .now() + ScannerViewController.successPulseDuration) { [weak self] in
                self?.finish(cancelled: false, data: value, format: format)
            }
            return
        }

        let now = Date().timeIntervalSince1970
        if value == lastValue, now - lastFiredAt < repeatDebounceSeconds {
            return
        }

        triggerHapticFeedback()
        viewfinderOverlay.playSuccessPulse(duration: ScannerViewController.successPulseDuration, targetRect: codeRect)
        lastValue = value
        lastFiredAt = now

        LaravelBridge.shared.send?(ScannerFunctions.codeScannedEvent, [
            "data": value,
            "format": format,
            "id": sessionId,
        ])
    }

    private func triggerHapticFeedback() {
        guard hapticsEnabled else { return }
        impactFeedback.impactOccurred()
    }

    private static func formatName(for type: AVMetadataObject.ObjectType, value: String, requested: [String]) -> String {
        if type == .ean13, value.count == 13, value.hasPrefix("0"),
           requested.contains("upca") || requested.contains("all"), !requested.contains("ean13") {
            return "upca"
        }

        switch type {
        case .qr: return "qr"
        case .ean13: return "ean13"
        case .ean8: return "ean8"
        case .code128: return "code128"
        case .code39: return "code39"
        case .upce: return "upce"
        case .code93: return "code93"
        case .pdf417: return "pdf417"
        case .aztec: return "aztec"
        case .dataMatrix: return "data_matrix"
        case .interleaved2of5: return "itf"
        case .itf14: return "itf14"
        case .codabar: return "codabar"
        default: return "unknown"
        }
    }

    private static func formatName(forSymbology symbology: VNBarcodeSymbology, value: String, requested: [String]) -> String {
        if symbology == .ean13, value.count == 13, value.hasPrefix("0"),
           requested.contains("upca") || requested.contains("all"), !requested.contains("ean13") {
            return "upca"
        }

        switch symbology {
        case .qr: return "qr"
        case .ean13: return "ean13"
        case .ean8: return "ean8"
        case .code128: return "code128"
        case .code39: return "code39"
        case .upce: return "upce"
        case .code93: return "code93"
        case .pdf417: return "pdf417"
        case .aztec: return "aztec"
        case .dataMatrix: return "data_matrix"
        case .itf14: return "itf14"
        case .codabar: return "codabar"
        default: return "unknown"
        }
    }

    fileprivate func loadAndDecode(provider: NSItemProvider) {
        provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
            guard let self = self else { return }
            guard let image = object as? UIImage, let cgImage = image.cgImage else {
                DispatchQueue.main.async {
                    self.showGalleryAlert(message: "Couldn't read that image.")
                }
                return
            }
            self.decodeGalleryImage(cgImage)
        }
    }

    private func decodeGalleryImage(_ cgImage: CGImage) {
        let request = VNDetectBarcodesRequest()
        request.symbologies = ScannerFunctions.barcodeSymbologies(for: requestedFormats)

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])

        do {
            try handler.perform([request])
        } catch {
            DispatchQueue.main.async { [weak self] in
                self?.showGalleryAlert(message: "Couldn't read that image.")
            }
            return
        }

        guard let match = request.results?.first(where: { $0.payloadStringValue != nil }),
              let value = match.payloadStringValue else {
            DispatchQueue.main.async { [weak self] in
                self?.showGalleryAlert(message: "No code found in that image.")
            }
            return
        }

        let format = ScannerViewController.formatName(forSymbology: match.symbology, value: value, requested: requestedFormats)

        DispatchQueue.main.async { [weak self] in
            self?.triggerHapticFeedback()
            self?.finish(cancelled: false, data: value, format: format)
        }
    }

    private func showGalleryAlert(message: String) {
        guard !finished else { return }
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    func finish(cancelled: Bool, data: String? = nil, format: String? = nil, reason: String? = nil) {
        guard !finished else { return }
        finished = true

        if ScannerFunctions.activeController === self {
            ScannerFunctions.activeController = nil
        }

        timeoutWorkItem?.cancel()

        if session.isRunning {
            session.stopRunning()
        }

        dismiss(animated: true)

        if cancelled {
            guard reason != nil else { return }

            LaravelBridge.shared.send?(ScannerFunctions.cancelledEvent, [
                "reason": reason,
                "id": sessionId,
            ])
        } else {
            LaravelBridge.shared.send?(ScannerFunctions.codeScannedEvent, [
                "data": data,
                "format": format ?? "unknown",
                "id": sessionId,
            ])
        }
    }
}

extension ScannerViewController: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        !(touch.view is UIControl)
    }
}

extension ScannerViewController: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else {
            picker.dismiss(animated: true)
            return
        }

        picker.dismiss(animated: true) { [weak self] in
            self?.loadAndDecode(provider: provider)
        }
    }
}