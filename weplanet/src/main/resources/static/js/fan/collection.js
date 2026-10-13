/** 나의 컬렉션 전체보기 모달 - /collection/{artistId} 배지를 받아 획득은 컬러, 미획득은 흑백으로 표시. */
(function () {
  "use strict";

  const modal = document.getElementById("collectionDetail");
  if (!modal) return;

  /** 배지 한 칸 생성 */
  function createBadgeCell(badge) {
    const cell = document.createElement("div");
    cell.className = "badge-cell" + (badge.earned ? "" : " is-locked");
    // 흑백 배지에 획득 조건 툴팁
    cell.title = badge.description || badge.badgeName;

    const icon = document.createElement("div");
    icon.className = "badge-cell__icon";

    if (badge.imageUrl) {
      // 이미지는 서버가 컬러·흑백 파일을 골라 주므로 CSS 필터를 걸지 않는다.
      icon.classList.add("has-image");
      const img = document.createElement("img");
      img.src = badge.imageUrl;
      img.alt = badge.badgeName;
      icon.appendChild(img);
    } else {
      icon.textContent = badge.icon;
    }

    const name = document.createElement("div");
    name.className = "badge-cell__name";
    // textContent 로 넣어 XSS 를 막는다.
    name.textContent = badge.badgeName;

    cell.appendChild(icon);
    cell.appendChild(name);
    return cell;
  }

  /** 배지 목록을 그리드에 채운다 */
  function fillGrid(gridId, badges) {
    const grid = document.getElementById(gridId);
    grid.innerHTML = "";
    badges.forEach(function (badge) {
      grid.appendChild(createBadgeCell(badge));
    });
  }

  /** 서버에서 받아 모달을 채우고 연다. */
  async function openCollection(artistId, artistName) {
    document.getElementById("collectionArtistName").textContent = artistName;

    try {
      const response = await fetch("/collection/" + artistId);
      if (!response.ok) {
        WePlaNet.alert(WePlaNet.t ? WePlaNet.t("client.collection.badgeLoadFailed", "배지 정보를 불러오지 못했습니다.") : "배지 정보를 불러오지 못했습니다.");
        return;
      }
      const data = await response.json();

      document.getElementById("achievementRate").textContent = data.achievementRate + "%";
      document.getElementById("achievementFill").style.width = data.achievementRate + "%";
      document.getElementById("earnedCount").textContent = data.earnedCount;
      document.getElementById("totalCount").textContent = data.totalCount;

      fillGrid("basicBadgeGrid", data.basicBadges);
      fillGrid("specialBadgeGrid", data.specialBadges);

      modal.classList.add("is-open");
    } catch (e) {
      WePlaNet.alert(WePlaNet.t ? WePlaNet.t("client.collection.badgeLoadFailed", "배지 정보를 불러오지 못했습니다.") : "배지 정보를 불러오지 못했습니다.");
    }
  }

  // 전체보기 버튼은 이벤트 위임으로 처리
  document.addEventListener("click", function (e) {
    const button = e.target.closest("[data-collection-open]");
    if (!button) return;
    openCollection(button.dataset.artistId, button.dataset.artistName);
  });
})();