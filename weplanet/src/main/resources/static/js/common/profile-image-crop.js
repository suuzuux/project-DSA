/** 프로필·배경 이미지 자르기 모달 (Cropper.js, 포털·커뮤니티 프로필 공용). */
(function () {
  "use strict";

  // 종류별 비율·마스크·출력 크기 (아바타 1:1 원형, 헤더 3:1)
  let KIND_CONFIG = {
    Avatar: {
      aspectRatio: 1,
      round: true,
      outputWidth: 500,
      outputHeight: 500,
      title: "프로필 이미지 편집"
    },
    Background: {
      aspectRatio: 2560 / 1660,
      round: false,
      outputWidth: 2560,
      outputHeight: 1660,
      title: "배경 이미지 편집",

      letterboxAtMinZoom: true
    },
    // 에이전시 헤더 이미지 크롭 설정 (3:1)
    HeaderImage: {
      aspectRatio: 3,
      round: false,
      outputWidth: 2400,
      outputHeight: 800,
      title: "헤더 이미지 편집",
      letterboxAtMinZoom: true
    }
  };

  let modal, modalTitle, stage, image, zoomRange, applyBtn;
  let cropper = null;
  let activeKind = null;
  // 크롭 설정 조회용 kind
  let activeConfigKind = null;
  let activeInput = null;
  let activePreviewEl = null;
  let activeLabelEl = null;
  let initialized = false;

  function ensureModal() {
    if (initialized) return !!modal;
    initialized = true;

    modal = document.getElementById("imageCropModal");
    if (!modal) return false;

    modalTitle = document.getElementById("imageCropModalTitle");
    stage = document.getElementById("imageCropStage");
    image = document.getElementById("imageCropTarget");
    zoomRange = document.getElementById("imageCropZoom");
    applyBtn = document.getElementById("imageCropApply");

    zoomRange.addEventListener("input", function () {
      if (cropper) cropper.zoomTo(Number(zoomRange.value));
    });

    // 휠·핀치 확대 시 슬라이더도 맞춘다.
    image.addEventListener("zoom", function (e) {
      if (e.detail && typeof e.detail.ratio === "number") {
        zoomRange.value = e.detail.ratio.toFixed(2);
      }
    });

    applyBtn.addEventListener("click", applyCrop);
    modal.querySelectorAll("[data-crop-cancel]").forEach(function (btn) {
      btn.addEventListener("click", function () { close(true); });
    });

    return true;
  }

  function destroyCropper() {
    if (cropper) {
      cropper.destroy();
      cropper = null;
    }
  }

  // 취소일 때만 input 을 비운다.
  function close(clearInput) {
    destroyCropper();
    if (modal) modal.classList.remove("is-open");
    if (clearInput && activeInput) {
      activeInput.value = "";
      if (activeLabelEl) activeLabelEl.textContent = "";
    }
    activeKind = null;
    activeConfigKind = null;
    activeInput = null;
    activePreviewEl = null;
    activeLabelEl = null;
  }

  function open(kind, file, inputEl, previewEl, labelEl, configKind) {
    if (!ensureModal() || typeof window.Cropper === "undefined") return false;
    let cfg = KIND_CONFIG[configKind || kind];
    if (!cfg || !file) return false;

    activeKind = kind;
    activeConfigKind = configKind || kind;
    activeInput = inputEl;
    activePreviewEl = previewEl || null;
    activeLabelEl = labelEl || null;

    // 모달 제목은 화면 언어 문구로 받는다 (없으면 한국어 기본값).
    var overrideTitles = window.WEPLANET_PROFILE_CROP_TITLES;
    modalTitle.textContent = (overrideTitles && (overrideTitles[activeConfigKind] || overrideTitles[kind])) || cfg.title;
    stage.classList.toggle("is-round", !!cfg.round);

    let reader = new FileReader();
    reader.onload = function (e) {
      destroyCropper();
      image.src = e.target.result;
      modal.classList.add("is-open");
      cropper = new Cropper(image, {
        aspectRatio: cfg.aspectRatio,
        // 배경은 프레임보다 작게 축소할 수 있도록 viewMode 0, 아바타는 1
        viewMode: cfg.letterboxAtMinZoom ? 0 : 1,
        dragMode: "move",
        autoCropArea: 1,
        cropBoxMovable: false,
        cropBoxResizable: false,
        toggleDragModeOnDblclick: false,
        background: false,
        ready: function () {
          if (cfg.letterboxAtMinZoom) {
            // 배경은 처음엔 이미지 전체가 보이게 시작한다.
            let cropBoxData = cropper.getCropBoxData();
            let imgData = cropper.getImageData();
            let naturalW = imgData.naturalWidth || 1;
            let naturalH = imgData.naturalHeight || 1;
            let containRatio = Math.min(cropBoxData.width / naturalW, cropBoxData.height / naturalH);
            let coverRatio = Math.max(cropBoxData.width / naturalW, cropBoxData.height / naturalH);

            cropper.zoomTo(containRatio);

            zoomRange.min = containRatio.toFixed(3);
            zoomRange.max = (coverRatio * 3).toFixed(3);
            zoomRange.step = 0.001;
            zoomRange.value = containRatio.toFixed(3);
          } else {
            // 아바타는 이미지별 처음 배율을 기준으로 슬라이더 범위를 잡는다.
            let startImgData = cropper.getImageData();
            let startRatio = startImgData.naturalWidth ? startImgData.width / startImgData.naturalWidth : 1;

            cropper.zoomTo(startRatio * 0.01);
            let clampedImgData = cropper.getImageData();
            let minRatio = clampedImgData.naturalWidth
              ? clampedImgData.width / clampedImgData.naturalWidth
              : startRatio;
            cropper.zoomTo(startRatio);

            zoomRange.min = minRatio.toFixed(2);
            zoomRange.max = (startRatio * 4).toFixed(2);
            zoomRange.step = 0.01;
            zoomRange.value = startRatio.toFixed(2);
          }
        }
      });
    };
    reader.readAsDataURL(file);
    return true;
  }

  function applyCrop() {
    if (!cropper || !activeKind) return;
    let cfg = KIND_CONFIG[activeConfigKind || activeKind];
    let canvas = cropper.getCroppedCanvas({
      width: cfg.outputWidth,
      height: cfg.outputHeight,
      imageSmoothingQuality: "high",
      // 빈 자리는 흰색으로 채운다.
      fillColor: "#ffffff"
    });
    if (!canvas) {
      close(true);
      return;
    }

    let kind = activeKind;
    let inputEl = activeInput;
    let previewEl = activePreviewEl;
    let labelEl = activeLabelEl;

    canvas.toBlob(function (blob) {
      if (!blob) {
        close(true);
        return;
      }

      let fileName = "profile-" + kind.toLowerCase() + ".jpg";
      let file = new File([blob], fileName, { type: "image/jpeg" });

      // 잘라낸 결과를 원래 input 에 넣는다 (폼 전송용).
      let dt = new DataTransfer();
      dt.items.add(file);
      inputEl.files = dt.files;

      if (labelEl) labelEl.textContent = fileName;

      let url = URL.createObjectURL(blob);
      if (previewEl) {
        previewEl.classList.remove("is-empty");
        if (kind === "Avatar") {
          previewEl.innerHTML =
            '<img src="' + url + '" alt="" style="width:100%;height:100%;object-fit:cover;display:block;">';
        } else {
          // 배경 미리보기는 실제 이미지로 채운다.
          previewEl.innerHTML = '<img src="' + url + '" alt="">';
        }
      }

      let flag = document.getElementById("remove" + kind + "Flag");
      if (flag) flag.value = "false";

      close(false);
    }, "image/jpeg", 0.9);
  }

  window.ProfileImageCrop = { open: open };
})();
