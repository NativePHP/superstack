const baseUrl = "/_native/api/call";

const VALID_FORMATS = [
  "qr",
  "ean13",
  "ean8",
  "code128",
  "code39",
  "upca",
  "upce",
  "all",
];

async function bridgeCall(method, params = {}) {
  const response = await fetch(baseUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-TOKEN":
        document.querySelector('meta[name="csrf-token"]')?.content || "",
    },
    body: JSON.stringify({ method, params }),
  });

  const result = await response.json();

  if (result.status === "error") {
    const error = new Error(
      result.message || "The scanner could not be started.",
    );
    error.code = result.code;
    throw error;
  }

  return result.data;
}

class PendingScan {
  constructor() {
    this._id = null;
    this._prompt = null;
    this._continuous = false;
    this._allowGallery = true;
    this._formats = ["qr"];
    this._haptics = true;
    this._zoom = 1.0;
    this._maxZoom = 3.0;
    this._zoomControl = true;
    this._focusOnTap = true;
    this._timeout = 0;
    this._started = false;
  }

  prompt(prompt) {
    this._prompt = prompt;
    return this;
  }

  continuous(continuous = true) {
    this._continuous = continuous;
    return this;
  }

  gallery(allow = true) {
    this._allowGallery = allow;
    return this;
  }

  haptics(enabled = true) {
    this._haptics = enabled;
    return this;
  }

  zoom(ratio = 1.0) {
    if (typeof ratio !== "number" || ratio <= 0) {
      throw new Error("Zoom ratio must be a positive number.");
    }
    this._zoom = ratio;
    return this;
  }

  maxZoom(ratio = 3.0) {
    if (typeof ratio !== "number" || ratio <= 0) {
      throw new Error("Max zoom ratio must be a positive number.");
    }
    this._maxZoom = ratio;
    return this;
  }

  zoomControl(enabled = true) {
    this._zoomControl = enabled;
    return this;
  }

  focusOnTap(enabled = true) {
    this._focusOnTap = enabled;
    return this;
  }

  timeout(seconds = 0) {
    if (typeof seconds !== "number" || seconds < 0) {
      throw new Error("Timeout must be zero (disabled) or a positive number of seconds.");
    }
    this._timeout = seconds;
    return this;
  }

  formats(formats) {
    if (!Array.isArray(formats) || formats.length === 0) {
      throw new Error("At least one barcode format must be specified.");
    }

    const invalid = formats.filter((format) => !VALID_FORMATS.includes(format));
    if (invalid.length > 0) {
      throw new Error(
        `Invalid barcode format(s): ${invalid.join(", ")}. Valid formats are: ${VALID_FORMATS.join(", ")}.`,
      );
    }

    this._formats = [...new Set(formats)];
    return this;
  }

  id(id) {
    this._id = id;
    return this;
  }

  getId() {
    return this._id;
  }

  then(resolve, reject) {
    if (this._started) {
      return resolve();
    }
    this._started = true;

    return bridgeCall("MobileScanner.Scan", {
      prompt: this._prompt ?? "",
      continuous: this._continuous,
      allowGallery: this._allowGallery,
      formats: this._formats,
      haptics: this._haptics,
      zoom: this._zoom,
      maxZoom: this._maxZoom,
      zoomControl: this._zoomControl,
      focusOnTap: this._focusOnTap,
      timeout: this._timeout,
      id: this._id,
    }).then(resolve, reject);
  }
}

export const Scanner = {
  scan: () => new PendingScan(),

  stop: (id) => bridgeCall("MobileScanner.Stop", { id: id ?? null }),
};

export { PendingScan };

const _eventListeners = {};
let _listenerInstalled = false;

function installListener() {
  if (_listenerInstalled) {
    return;
  }

  document.addEventListener("native-event", (e) => {
    const eventName = e.detail.event.replace(/^(\\)+/, "");
    const payload = e.detail.payload;
    (_eventListeners[eventName] || []).forEach((callback) =>
      callback(payload, eventName),
    );
  });

  _listenerInstalled = true;
}

export function On(eventName, callback) {
  installListener();
  (_eventListeners[eventName] ??= []).push(callback);
}

export function Off(eventName, callback) {
  if (_eventListeners[eventName]) {
    _eventListeners[eventName] = _eventListeners[eventName].filter(
      (cb) => cb !== callback,
    );
  }
}

export const Events = {
  Scanner: {
    CodeScanned: "Sandip\\Scanner\\Native\\Events\\Scanner\\CodeScanned",
    Cancelled: "Sandip\\Scanner\\Native\\Events\\Scanner\\Cancelled",
  },
};

export default { Scanner, On, Off, Events, PendingScan };
