/**
 * ============================================================
 * WePlaNet – 프로필 / 배경 이미지 크롭 모달 (PROFILE-04)
 * ------------------------------------------------------------
 * "사진첩에서 불러오기"로 이미지를 고르면, 업로드 전에 위치를 옮기고
 * 확대/축소해서 원하는 영역만 잘라낼 수 있게 하는 공용 모듈이다.
 * portal/profile.html, community/profile.html 두 화면이 같은
 * <div class="modal-backdrop" id="imageCropModal"> 마크업 + 이 스크립트를
 * 그대로 함께 쓴다 (이미지 편집 버튼/파일 인풋 id 구조가 두 화면에서 동일함).
 * ============================================================
 */
(function () {
  "use strict";

  // 종류별 크롭 비율/마스크/출력 크기.
  // 아바타는 카드에서 원형(border-radius:50%)으로 쓰이니 1:1 정사각형 + 원형 마스크,
  // 배경은 profile-card__cover(가로로 넓은 커버 영역)에 맞춘 비율로 자른다.
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
    // 에이전시 헤더 이미지(커뮤니티 히어로 배너 원본) 크롭 설정 - 3:1.
    // community/profile.html의 개인 프로필 커버(Background, 2560:1660)와는 별개의 설정이다.
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
  // 크롭 비율(KIND_CONFIG) 조회용 kind. DOM id 조회는 activeKind를 그대로 쓴다.
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

    // 이미지를 휠/핀치로 직접 확대해도 슬라이더 위치가 같이 따라오게 동기화
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

  // clearInput: 취소일 때만 true - 적용 후에는 이미 새 파일을 넣어뒀으니 지우면 안 됨
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

    // SETTINGS-03: portal/profile.html은 화면 언어에 맞춘 제목을 window.WEPLANET_PROFILE_CROP_TITLES로 넘겨줌.
    // 그 값이 없으면 기존 한국어 기본값(cfg.title)을 그대로 씀.
    // 크롭 설정(configKind, 예: 포털 배경 → HeaderImage)의 제목을 먼저 찾고, 없으면 kind 제목을 쓴다.
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
        // 배경(letterboxAtMinZoom)은 이미지가 크롭 프레임보다 작아지는 상태(letterbox)까지
        // 허용해야 해서 viewMode:0(무제한). 아바타는 기존처럼 프레임을 항상 채우도록 1 유지.
        viewMode: cfg.letterboxAtMinZoom ? 0 : 1,
        dragMode: "move",
        autoCropArea: 1,
        cropBoxMovable: false,
        cropBoxResizable: false,
        toggleDragModeOnDblclick: false,
        background: false,
        ready: function () {
          if (cfg.letterboxAtMinZoom) {
            // 배경: 가장 축소했을 때 이미지 전체가 잘리지 않고 다 보이도록(contain fit) 시작하고,
            // 확대할수록 점점 크롭 프레임을 채우며 잘려나가게 한다.
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
            // 아바타: 이미지마다 처음 맞춰지는 배율이 달라서, 그 배율을 기준으로 슬라이더 범위를 잡는다.
            // Cropper.js는 크롭 프레임보다 이미지가 작아지는 축소는 자동으로 막아버려서(viewMode:1),
            // "ratio * 0.5"처럼 임의로 하한을 잡으면 슬라이더는 움직이는데 실제로는 이미 바닥이라
            // 화면이 안 바뀌는 상황이 생긴다. 그래서 아주 작은 값으로 실제로 줌을 시도해보고,
            // Cropper가 실제로 적용해준 배율을 진짜 축소 하한선으로 쓴다.
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
      // 배경을 가장 축소한 상태(letterbox)로 적용하면 프레임 일부가 이미지로 안 채워질 수 있는데,
      // 그 빈 자리를 검은색 대신 흰색으로 채운다 (배경을 아직 안 정했을 때의 패널 색과 동일).
      // 2560x1660으로 바꾼 뒤로는 실제로 이 자리가 채워질 일이 거의 없지만 안전장치로 남겨둔다.
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

      // 잘라낸 결과를 원래 input에 다시 넣는다 - 폼 전송 시 이 파일이 그대로 올라감
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
          // 배경은 background-image가 아니라 실제 <img>(width:100%;height:auto)로 채운다 -
          // 박스 너비에 맞춰 이미지를 늘리고 높이는 원본 비율을 그대로 따라가서, 좌우가
          // 잘리거나 흰 여백이 남는 일 없이 항상 이미지 전체가 꽉 차게 보장된다.
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
