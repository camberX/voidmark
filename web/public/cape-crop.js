window.VoidmarkCapeCrop = (function () {
	var ASPECT = 10 / 16;
	var FACE_W = 10;
	var FACE_H = 16;
	var LAYOUT_W = 64;
	var LAYOUT_H = 32;
	var MAX_SCALE = 16;
	var overlay = null;

	function isVanilla(w, h) {
		return w >= 64 && h >= 32 && w % 64 === 0 && h % 32 === 0 && w / 64 === h / 32;
	}

	function cover(srcW, srcH) {
		var srcAspect = srcW / Math.max(1, srcH);
		var crop = { x: 0, y: 0, w: 1, h: 1 };
		if (srcAspect > ASPECT) {
			crop.h = 1;
			crop.w = ASPECT / srcAspect;
			crop.x = (1 - crop.w) * 0.5;
			crop.y = 0;
		} else {
			crop.w = 1;
			crop.h = srcAspect / ASPECT;
			crop.x = 0;
			crop.y = (1 - crop.h) * 0.5;
		}
		return crop;
	}

	function clampCrop(crop, srcW, srcH) {
		srcW = Math.max(1, srcW);
		srcH = Math.max(1, srcH);
		var srcAspect = srcW / srcH;
		var normAspect = ASPECT / srcAspect;
		var max = cover(srcW, srcH);
		var minW = Math.max(10 / srcW, max.w * 0.12);
		crop.w = Math.min(max.w, Math.max(minW, crop.w));
		crop.h = crop.w / normAspect;
		if (crop.h > max.h) {
			crop.h = max.h;
			crop.w = crop.h * normAspect;
		}
		crop.x = Math.min(Math.max(0, crop.x), Math.max(0, 1 - crop.w));
		crop.y = Math.min(Math.max(0, crop.y), Math.max(0, 1 - crop.h));
	}

	function loadImage(file) {
		return new Promise(function (resolve, reject) {
			var url = URL.createObjectURL(file);
			var img = new Image();
			img.onload = function () {
				URL.revokeObjectURL(url);
				resolve(img);
			};
			img.onerror = function () {
				URL.revokeObjectURL(url);
				reject(new Error("Could not read that image."));
			};
			img.src = url;
		});
	}

	function pickScale(sw, sh) {
		var needed = Math.max(4, Math.min(MAX_SCALE, Math.max(Math.floor(sw / FACE_W), Math.floor(sh / FACE_H))));
		return Math.min(MAX_SCALE, Math.max(4, needed));
	}

	function bakeAtlas(img, crop) {
		var sw = img.width;
		var sh = img.height;
			var scale = pickScale(img.width, img.height);
		var aw = LAYOUT_W * scale;
		var ah = LAYOUT_H * scale;
		var out = document.createElement("canvas");
		out.width = aw;
		out.height = ah;
		var ctx = out.getContext("2d");
		ctx.fillStyle = "#000";
		ctx.fillRect(0, 0, aw, ah);
		ctx.imageSmoothingEnabled = crop.w * sw > 10 * scale || crop.h * sh > 16 * scale;
		var fu = scale;
		var fv = scale;
		var fw = FACE_W * scale;
		var fh = FACE_H * scale;
		var sx = crop.x * sw;
		var sy = crop.y * sh;
		var sWidth = crop.w * sw;
		var sHeight = crop.h * sh;
		ctx.drawImage(img, sx, sy, sWidth, sHeight, fu, fv, fw, fh);
		ctx.drawImage(out, fu, fv, fw, fh, 12 * scale, fv, fw, fh);
		var edge = ctx.getImageData(fu, fv, fw, fh);
		for (var y = 0; y < fh; y++) {
			var left = (y * fw) * 4;
			var right = (y * fw + fw - 1) * 4;
			for (var x = 0; x < scale; x++) {
				ctx.fillStyle = "rgb(" + edge.data[left] + "," + edge.data[left + 1] + "," + edge.data[left + 2] + ")";
				ctx.fillRect(x, fv + y, 1, 1);
				ctx.fillStyle = "rgb(" + edge.data[right] + "," + edge.data[right + 1] + "," + edge.data[right + 2] + ")";
				ctx.fillRect(11 * scale + x, fv + y, 1, 1);
			}
		}
		for (var i = 0; i < fw; i++) {
			var top = i * 4;
			var bot = ((fh - 1) * fw + i) * 4;
			for (var t = 0; t < scale; t++) {
				ctx.fillStyle = "rgb(" + edge.data[top] + "," + edge.data[top + 1] + "," + edge.data[top + 2] + ")";
				ctx.fillRect(fu + i, t, 1, 1);
				ctx.fillStyle = "rgb(" + edge.data[bot] + "," + edge.data[bot + 1] + "," + edge.data[bot + 2] + ")";
				ctx.fillRect(11 * scale + i, t, 1, 1);
			}
		}
		return out;
	}

	function canvasPng(canvas) {
		return new Promise(function (resolve, reject) {
			canvas.toBlob(function (blob) {
				if (!blob) reject(new Error("Could not encode cape."));
				else resolve(blob);
			}, "image/png");
		});
	}

	function close() {
		if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
		overlay = null;
	}

	function open(file, onDone, onCancel) {
		close();
		loadImage(file).then(function (img) {
			if (isVanilla(img.width, img.height)) {
				onDone(file);
				return;
			}
			var crop = cover(img.width, img.height);
			overlay = document.createElement("div");
			overlay.className = "overlay center";
			overlay.innerHTML = ""
				+ '<div class="sheet crop-sheet">'
				+ "<h1 style=\"font-size:16px\">CAPE CREATOR</h1>"
				+ '<p class="who">Drag to pan. Scroll or pinch to zoom. The box is the 10×16 face other players see.</p>'
				+ '<div class="crop-wrap">'
				+ '<div class="crop-stage" id="crop-stage"><div class="crop-holder"><canvas id="crop-src"></canvas><div id="crop-box"></div></div></div>'
				+ '<div class="crop-side"><div class="crop-face"><canvas id="crop-face"></canvas></div><p class="hint">In-game face</p></div>'
				+ "</div>"
				+ '<div class="row">'
				+ '<button type="button" class="ghost" id="crop-reset">Reset</button>'
				+ '<button type="button" class="ghost" id="crop-cancel">Cancel</button>'
				+ '<button type="button" class="primary" id="crop-apply">Apply cape</button>'
				+ "</div></div>";
			document.body.appendChild(overlay);
			var stage = overlay.querySelector("#crop-stage");
			var srcCanvas = overlay.querySelector("#crop-src");
			var faceCanvas = overlay.querySelector("#crop-face");
			var box = overlay.querySelector("#crop-box");
			var dragging = false;
			var lastX = 0;
			var lastY = 0;

			function layout() {
				var maxW = Math.min(420, stage.clientWidth || 420);
				var maxH = 280;
				var fit = Math.min(maxW / img.width, maxH / img.height);
				srcCanvas.width = Math.max(1, Math.round(img.width * fit));
				srcCanvas.height = Math.max(1, Math.round(img.height * fit));
				var sctx = srcCanvas.getContext("2d");
				sctx.imageSmoothingEnabled = true;
				sctx.drawImage(img, 0, 0, srcCanvas.width, srcCanvas.height);
				box.style.left = crop.x * srcCanvas.width + "px";
				box.style.top = crop.y * srcCanvas.height + "px";
				box.style.width = crop.w * srcCanvas.width + "px";
				box.style.height = crop.h * srcCanvas.height + "px";
				faceCanvas.width = 50;
				faceCanvas.height = 80;
				var fctx = faceCanvas.getContext("2d");
				fctx.imageSmoothingEnabled = true;
				fctx.drawImage(
					img,
					crop.x * img.width,
					crop.y * img.height,
					crop.w * img.width,
					crop.h * img.height,
					0,
					0,
					50,
					80
				);
			}

			overlay.querySelector("#crop-reset").onclick = function () {
				crop = cover(img.width, img.height);
				layout();
			};
			overlay.querySelector("#crop-cancel").onclick = function () {
				close();
				if (onCancel) onCancel();
			};
			overlay.querySelector("#crop-apply").onclick = function () {
				canvasPng(bakeAtlas(img, crop)).then(function (blob) {
					close();
					onDone(blob);
				}).catch(function (error) {
					alert(error.message);
				});
			};
			overlay.addEventListener("click", function (event) {
				if (event.target === overlay) {
					close();
					if (onCancel) onCancel();
				}
			});
			stage.addEventListener("pointerdown", function (event) {
				dragging = true;
				lastX = event.clientX;
				lastY = event.clientY;
				stage.setPointerCapture(event.pointerId);
			});
			stage.addEventListener("pointerup", function () { dragging = false; });
			stage.addEventListener("pointermove", function (event) {
				if (!dragging) return;
				var dx = (event.clientX - lastX) / srcCanvas.width;
				var dy = (event.clientY - lastY) / srcCanvas.height;
				lastX = event.clientX;
				lastY = event.clientY;
				crop.x += dx;
				crop.y += dy;
				clampCrop(crop, img.width, img.height);
				layout();
			});
			stage.addEventListener("wheel", function (event) {
				event.preventDefault();
				var rect = srcCanvas.getBoundingClientRect();
				var px = (event.clientX - rect.left) / srcCanvas.width;
				var py = (event.clientY - rect.top) / srcCanvas.height;
				var factor = event.deltaY < 0 ? 0.88 : 1.14;
				crop.x = px - (px - crop.x) * factor;
				crop.y = py - (py - crop.y) * factor;
				crop.w *= factor;
				crop.h *= factor;
				clampCrop(crop, img.width, img.height);
				layout();
			}, { passive: false });
			requestAnimationFrame(layout);
		}).catch(function (error) {
			if (onCancel) onCancel();
			alert(error.message);
		});
	}

	return { open: open, isVanilla: isVanilla };
})();
