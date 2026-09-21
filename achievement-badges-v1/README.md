# 업적 배지 아이콘 세트 v1

- 총 25종
- 획득 상태: `color/`
- 미획득 상태: `grayscale/`
- 모든 아이콘은 `64 × 64` viewBox, 투명 배경 SVG
- 컬러/미획득 버전은 도형이 완전히 같고 색상만 다릅니다.

## 가장 간단한 사용법

```html
<img
  src={earned
    ? "/badges/color/community-first-join.svg"
    : "/badges/grayscale/community-first-join.svg"}
  width="64"
  height="64"
  alt="커뮤니티 첫 가입"
/>
```

레이아웃이 흔들리지 않도록 `width`와 `height`를 함께 지정하세요. 전체 파일명과 한글 라벨 매핑은 `manifest.json`에 있습니다.

## SVG 스프라이트 사용

개별 요청 수를 줄이고 싶다면 `sprite-color.svg`, `sprite-grayscale.svg`를 사용할 수 있습니다.

```html
<svg width="64" height="64" role="img" aria-label="커뮤니티 첫 가입">
  <use href="/badges/sprite-color.svg#community-first-join" />
</svg>
```
