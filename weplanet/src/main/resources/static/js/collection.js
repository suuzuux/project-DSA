/**
 * 나의 컬렉션 - 전체보기 모달
 * ------------------------------------------------------------
 * 카드의 "전체보기"를 누르면 /collection/{artistId} 를 호출해서
 * 배지 25개를 받아 모달에 그린다.
 * 획득한 배지는 컬러, 못 받은 배지는 흑백(CSS is-locked)으로 표시한다.
 */
(function () {
  "use strict";

  const modal = document.getElementById("collectionDetail");
  if (!modal) return;

  /** 배지 한 칸을 만든다 */
  function createBadgeCell(badge) {
    const cell = document.createElement("div");
    cell.className = "badge-cell" + (badge.earned ? "" : " is-locked");
    // 흑백 배지에 마우스를 올리면 획득 조건이 보이게 함
    cell.title = badge.description || badge.badgeName;

    const icon = document.createElement("div");
    icon.className = "badge-cell__icon";

    if (badge.imageUrl) {
      const img = document.createElement("img");
      img.src = badge.imageUrl;
      img.alt = badge.badgeName;
      icon.appendChild(img);
    } else {
      icon.textContent = badge.icon;
    }

    const name = document.createElement("div");
    name.className = "badge-cell__name";
    // textContent 를 쓰면 배지 이름에 태그가 들어와도 글자로만 표시된다(XSS 방지)
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

  /** 서버에서 받아 모달을 채우고 연다 */
  async function openCollection(artistId, artistName) {
    document.getElementById("collectionArtistName").textContent = artistName;

    try {
      const response = await fetch("/collection/" + artistId);
      if (!response.ok) {
        WePlaNet.alert("배지 정보를 불러오지 못했습니다.");
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
      WePlaNet.alert("배지 정보를 불러오지 못했습니다.");
    }
  }

  // 카드가 여러 장이라 버튼마다 리스너를 다는 대신, 문서에 하나만 달고
  // 클릭된 대상이 "전체보기" 버튼인지 확인한다(이벤트 위임)
  document.addEventListener("click", function (e) {
    const button = e.target.closest("[data-collection-open]");
    if (!button) return;
    openCollection(button.dataset.artistId, button.dataset.artistName);
  });
})();