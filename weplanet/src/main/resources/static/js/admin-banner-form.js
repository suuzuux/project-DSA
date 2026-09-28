/**
 * 최고관리자 > 배너 영역 관리 > 등록/수정 폼
 *  - 입력값이 바뀌면 위 미리보기 배너에 바로 반영
 *  - 이미지를 고르면 이미지의 평균색을 배경 색상에 자동으로 채움 (서버의 MainBannerService.averageColorOf 와 같은 방식)
 *  - 상품 홍보일 때만 상품 선택칸을 보여주고, 고른 아티스트의 상품만 남김
 */
(function () {
  "use strict";

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

  // 배경이 밝으면 차콜 글자, 어두우면 흰 글자 (서버 textColorFor 와 같은 기준)
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
    previewTitle.textContent = titleInput.value.trim() || "대제목을 입력하세요";
    previewBody.textContent = bodyInput.value.trim();
  }

  // 상품 홍보일 때만 상품칸을 보이고, 선택한 아티스트의 상품만 남긴다
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
      ? "먼저 아티스트를 선택하세요."
      : visibleCount === 0
        ? "이 아티스트는 판매 중인 상품이 없습니다."
        : "선택한 아티스트의 판매 중 상품만 보입니다.";
  }

  // 이미지 평균색 (가로세로 60칸 정도로 듬성듬성, 투명 픽셀 제외)
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
