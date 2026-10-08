/** 관리자 배너 폼 - 미리보기 즉시 반영, 이미지 평균색으로 배경색 자동 채움, 상품 홍보일 때만 상품 선택. */
(function () {
  "use strict";

  // 다국어 문구 (없으면 한국어 기본값)
  const I18N = window.ADMIN_I18N || {};
  function t(key, ko) { return I18N[key] != null ? I18N[key] : ko; }

  const form = document.querySelector("form[enctype='multipart/form-data']");
  if (!form) return;

  const preview = document.getElementById("bannerPreview");
  const previewLabel = document.getElementById("previewLabel");
  const previewTitle = document.getElementById("previewTitle");
  const previewBody = document.getElementById("previewBody");
  const previewImage = document.getElementById("previewImage");

  const typeRadios = form.querySelectorAll("input[name='bannerType']");
  const artistSelect = document.getElementById("artistId");
  const goodsGroup = document.getElementById("goodsGroup");
  const goodsSelect = document.getElementById("goodsId");
  const goodsEmptyHint = document.getElementById("goodsEmptyHint");
  const titleInput = document.getElementById("title");
  const bodyInput = document.getElementById("body");
  const imageInput = document.getElementById("image");
  const colorInput = document.getElementById("bgColor");

  function selectedType() {
    const checked = form.querySelector("input[name='bannerType']:checked");
    return checked ? checked.value : "COMMUNITY";
  }

  // 배경이 밝으면 차콜, 어두우면 흰 글자 (서버와 같은 기준)
  function textColorFor(hex) {
    const r = parseInt(hex.substr(1, 2), 16);
    const g = parseInt(hex.substr(3, 2), 16);
    const b = parseInt(hex.substr(5, 2), 16);
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return luminance > 0.62 ? "#2c2c2c" : "#ffffff";
  }

  function renderColor() {
    const bg = colorInput.value || "#4a4a4a";
    preview.style.background = bg;
    preview.style.color = textColorFor(bg);
  }

  function renderText() {
    const option = artistSelect.options[artistSelect.selectedIndex];
    let label = "ARTIST";
    if (option && option.value) {
      label = option.dataset.nameEn ? option.dataset.nameEn.toUpperCase() : option.textContent.trim();
    }
    previewLabel.textContent = label;
    previewTitle.textContent = titleInput.value.trim() || t("adminBanner.form.previewTitlePlaceholder", "대제목을 입력하세요");
    previewBody.textContent = bodyInput.value.trim();
  }

  // 상품 홍보일 때만 상품칸을 보이고 선택 아티스트 상품만 남긴다.
  function renderGoods() {
    const isProduct = selectedType() === "PRODUCT";
    goodsGroup.hidden = !isProduct;
    goodsSelect.required = isProduct;

    const artistId = artistSelect.value;
    let visibleCount = 0;
    Array.prototype.forEach.call(goodsSelect.options, function (opt) {
      if (!opt.value) return;
      const match = artistId && opt.dataset.artistId === artistId;
      opt.hidden = !match;
      opt.disabled = !match;
      if (match) visibleCount += 1;
      if (!match && opt.selected) goodsSelect.value = "";
    });
    goodsEmptyHint.textContent = !artistId
      ? t("adminBanner.form.goodsHintSelectArtist", "먼저 아티스트를 선택하세요.")
      : visibleCount === 0
        ? t("adminBanner.form.goodsHintNone", "이 아티스트는 판매 중인 상품이 없습니다.")
        : t("adminBanner.form.goodsHint", "선택한 아티스트의 판매 중 상품만 보입니다.");
  }

  // 이미지 평균색 (듬성듬성 샘플링, 투명 픽셀 제외)
  function averageColor(img) {
    const canvas = document.createElement("canvas");
    const w = (canvas.width = 60);
    const h = (canvas.height = Math.max(1, Math.round((img.naturalHeight / img.naturalWidth) * 60)));
    const ctx = canvas.getContext("2d");
    ctx.drawImage(img, 0, 0, w, h);
    const data = ctx.getImageData(0, 0, w, h).data;
    let r = 0, g = 0, b = 0, n = 0;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] < 128) continue;
      r += data[i]; g += data[i + 1]; b += data[i + 2]; n += 1;
    }
    if (n === 0) return null;
    const hex = function (v) { return Math.round(v / n).toString(16).padStart(2, "0"); };
    return "#" + hex(r) + hex(g) + hex(b);
  }

  imageInput.addEventListener("change", function () {
    const file = imageInput.files && imageInput.files[0];
    if (!file) return;
    const url = URL.createObjectURL(file);
    previewImage.onload = function () {
      const color = averageColor(previewImage);
      if (color) {
        colorInput.value = color;
        renderColor();
      }
    };
    previewImage.src = url;
    previewImage.classList.remove("hidden");
  });

  typeRadios.forEach(function (radio) { radio.addEventListener("change", renderGoods); });
  artistSelect.addEventListener("change", function () { renderText(); renderGoods(); });
  titleInput.addEventListener("input", renderText);
  bodyInput.addEventListener("input", renderText);
  colorInput.addEventListener("input", renderColor);

  renderText();
  renderGoods();
  renderColor();
})();
