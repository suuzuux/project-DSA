"""
WePlaNet 발표/테스트 배포용 데모 데이터 생성기 (정휘원 / 2026-10-04)

만드는 것
  - docs/demo/weplanet_demo_seed.sql : weplanet_schema_full_reset_v2.sql 을 실행한 "바로 다음"에 실행하는 SQL
  - docs/demo/demo_images/           : SQL 이 가리키는 이미지 파일들. 서버의 uploads/ 폴더에 그대로 복사한다

다시 만들기 (내용을 고친 뒤)
  python3 -m venv .venv && .venv/bin/pip install pillow
  .venv/bin/python docs/demo/generate_demo_seed.py

규칙
  - 실존 인물/그룹이 아닌 가상의 이름만 쓴다
  - id 는 기존 시드와 겹치지 않게 큰 번호대로 직접 지정한다 (users 1001~, post 10001~ 등)
  - 시각은 NOW() 기준 상대값이라 언제 실행해도 "최근 활동"처럼 보인다
  - random.seed 고정 -> 몇 번을 다시 만들어도 같은 결과
"""
import os
import random
import shutil

from PIL import Image, ImageDraw, ImageFilter, ImageFont

random.seed(20261004)

HERE = os.path.dirname(os.path.abspath(__file__))
IMG_DIR = os.path.join(HERE, "demo_images")
SQL_PATH = os.path.join(HERE, "weplanet_demo_seed.sql")
FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"

# 공통 비밀번호 Test1234 (기존 시드와 같은 BCrypt 해시)
PW = "$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu"


# ============================================================
# 데이터 정의
# ============================================================
AGENCIES = [
    # id, 이름, 사업자번호, 대표, 담당자 username, 담당자 닉네임
    (101, "스타라이트엔터테인먼트", "101-81-10101", "윤서진", "agency_starlight", "스타라이트 매니저"),
    (102, "블루웨이브뮤직", "102-81-10202", "강도현", "agency_bluewave", "블루웨이브 매니저"),
    (103, "문빔컴퍼니", "103-81-10303", "한지우", "agency_moonbeam", "문빔 매니저"),
]
AGENCY_USER_ID = {101: 1011, 102: 1012, 103: 1013}

ADMINS = [
    (1001, "admin_super1", "최고관리자1", "admin4.wp+1@gmail.com", "WP-ADM-001"),
    (1002, "admin_super2", "최고관리자2", "admin4.wp+2@gmail.com", "WP-ADM-002"),
    (1003, "admin_super3", "최고관리자3", "admin4.wp+3@gmail.com", "WP-ADM-003"),
    (1004, "admin_super4", "최고관리자4", "admin4.wp+4@gmail.com", "WP-ADM-004"),
]

# 커뮤니티 = 아티스트 계정(그룹/솔로). members 가 비어 있으면 솔로
COMMUNITIES = [
    dict(id=1101, slug="nova", username="nova_official", name="NOVA", name_en="NOVA", agency=101,
         fandom="스텔라", debut="2022-03-15", gender="MALE", nationality="KR", category="아이돌",
         color=(79, 70, 229), color2=(147, 51, 234),
         intro="밤하늘에서 가장 밝게 빛나는 다섯 개의 별, NOVA 공식 커뮤니티입니다 ✨",
         members=[("시온", "LEADER"), ("하람", "VOCAL"), ("유진", "VOCAL"), ("태오", "RAP"), ("리안", "DANCE")]),
    dict(id=1102, slug="lumi", username="lumi_official", name="LUMI", name_en="LUMI", agency=101,
         fandom="루미너스", debut="2023-06-01", gender="FEMALE", nationality="KR", category="아이돌",
         color=(236, 72, 153), color2=(251, 146, 60),
         intro="빛을 머금은 네 소녀 LUMI 💗 루미너스와 함께하는 공간",
         members=[("서아", "LEADER"), ("나윤", "VOCAL"), ("채린", "DANCE"), ("다온", "RAP")]),
    dict(id=1103, slug="eclipse", username="eclipse_official", name="ECLIPSE", name_en="ECLIPSE", agency=102,
         fandom="코로나", debut="2021-09-09", gender="MALE", nationality="KR", category="아이돌",
         color=(30, 41, 59), color2=(234, 179, 8),
         intro="빛과 어둠이 겹치는 순간, ECLIPSE 🌘 코로나 여러분 환영합니다",
         members=[("도윤", "LEADER"), ("지한", "VOCAL"), ("민호", "RAP"), ("카이", "DANCE"), ("우진", "VOCAL")]),
    dict(id=1104, slug="prism", username="prism_official", name="PRISM", name_en="PRISM", agency=103,
         fandom="스펙트럼", debut="2024-02-14", gender="MIXED", nationality="KR", category="아이돌",
         color=(16, 185, 129), color2=(59, 130, 246),
         intro="네 가지 색이 모여 만드는 무지개, 혼성그룹 PRISM 🌈",
         members=[("하나", "LEADER"), ("레오", "RAP"), ("소라", "VOCAL"), ("진", "DANCE")]),
    dict(id=1105, slug="yuri", username="yuri_official", name="한유리", name_en="HAN YURI", agency=102,
         fandom="유리알", debut="2020-05-20", gender="FEMALE", nationality="KR", category="솔로가수",
         color=(245, 158, 11), color2=(239, 68, 68),
         intro="싱어송라이터 한유리의 작은 방에 오신 걸 환영해요 🎹", members=[]),
    dict(id=1106, slug="kaito", username="kaito_official", name="KAITO", name_en="KAITO", agency=103,
         fandom="카이토모", debut="2022-11-11", gender="MALE", nationality="JP", category="솔로가수",
         color=(14, 165, 233), color2=(99, 102, 241),
         intro="도쿄에서 온 KAITO 입니다. 한국어 열심히 공부 중이에요! 🇯🇵🇰🇷", members=[]),
]

FANS = [
    # username, 닉네임(계정), 커뮤니티 닉네임(최대 10자)
    ("demo_fan01", "별빛수집가", "별빛수집가"), ("demo_fan02", "하람바라기", "하람바라기"),
    ("demo_fan03", "루미루미", "루미루미"), ("demo_fan04", "새벽세시", "새벽세시"),
    ("demo_fan05", "초코우유", "초코우유"), ("demo_fan06", "무지개다리", "무지개다리"),
    ("demo_fan07", "피아노소녀", "피아노소녀"), ("demo_fan08", "도쿄여행중", "도쿄여행중"),
    ("demo_fan09", "응원봉장인", "응원봉장인"), ("demo_fan10", "포카교환해요", "포카교환해요"),
    ("demo_fan11", "달토끼", "달토끼"), ("demo_fan12", "직캠러", "직캠러"),
    ("demo_fan13", "오늘도덕질", "오늘도덕질"), ("demo_fan14", "콘서트가자", "콘서트가자"),
    ("demo_fan15", "QA데모팬", "QA데모팬"),
]
FAN_ID0 = 1301

# 팬별 가입 커뮤니티 (인덱스) - 커뮤니티마다 가입자가 고르게 생기도록
FAN_JOINS = {
    0: [0, 1, 4], 1: [0, 2], 2: [1, 3, 5], 3: [0, 2, 4], 4: [1, 5], 5: [3, 0], 6: [4, 1],
    7: [5, 2], 8: [0, 1, 2, 3], 9: [1, 2, 4], 10: [3, 5, 0], 11: [2, 4], 12: [0, 3, 5],
    13: [1, 2, 3, 4, 5], 14: [0, 1, 2, 3, 4, 5],
}

ARTIST_POSTS = [
    ("오늘 연습 끝! 🕺", "오늘도 {fandom} 생각하면서 열심히 연습했어요!\n\n새로운 안무가 정말 멋있게 나올 것 같아요. 조금만 기다려 주세요 💪"),
    ("{fandom} 다들 밥 챙겨 먹었어요?", "저는 방금 김치찌개 먹었어요 🍲\n오늘 하루도 고생 많았어요. 따뜻하게 입고 다녀요!"),
    ("녹음실 비하인드 🎙️", "신곡 녹음하다가 잠깐 쉬는 시간!\n\n**이번 곡은 진짜 자신 있어요.** 가사에 {fandom} 이야기를 많이 담았어요."),
    ("팬사인회 와줘서 고마워요 💌", "한 분 한 분 얼굴 보면서 이야기 나눌 수 있어서 너무 행복했어요.\n받은 편지는 숙소 가서 하나도 빠짐없이 읽을게요!"),
    ("비 오는 날 추천곡 ☔", "비 오는 날엔 이 노래 꼭 들어보세요\n\n- 창밖을 보며 듣기\n- 따뜻한 차 한 잔과 함께\n\n여러분 추천곡도 댓글로 알려줘요!"),
    ("컴백 D-7 ⏰", "드디어 일주일 남았어요!!\n티저 사진 마음에 들었어요? 다음 티저도 기대해 주세요 😎"),
    ("무대 끝나고 한 컷 📸", "오늘 음악방송 무대 어땠어요?\n떨렸지만 {fandom} 함성 덕분에 끝까지 잘할 수 있었어요. 사랑해요 💜"),
    ("주말 잘 보내요 🌙", "이번 주도 정말 고생 많았어요.\n푹 쉬고 다음 주에 더 밝은 모습으로 만나요!"),
]
FAN_POSTS = [
    ("오늘 무대 레전드였다", "진짜 {name} 무대는 볼 때마다 새로워요ㅠㅠ\n특히 후렴 안무 너무 좋았어요 👏"),
    ("{name} 입덕 계기 공유해요", "저는 우연히 유튜브 직캠 보고 입덕했어요!\n여러분은 어떻게 {fandom} 되셨나요? 🥰"),
    ("포카 교환 원해요 🃏", "이번 앨범 포카 교환 구해요!\n댓글로 가지고 계신 포카 알려주세요~"),
    ("콘서트 티켓팅 성공했어요!!", "손 떨려서 죽는 줄 알았는데 성공했어요 😭\n같이 가시는 분들 현장에서 만나요!"),
    ("응원법 정리해봤어요 📣", "신곡 응원법 정리했어요!\n\n1. 인트로: 멤버 이름 순서대로\n2. 후렴: 박수 두 번\n3. 브릿지: 다 같이 {fandom}!"),
    ("생일카페 다녀왔어요 🎂", "컵홀더랑 포토존 너무 예뻤어요.\n준비해주신 분들 정말 감사합니다 💐"),
    ("요즘 출근길 플레이리스트", "출근길에 {name} 노래만 들어요.\n하루 시작이 행복해지는 마법 ✨"),
    ("굿즈 언박싱 📦", "응원봉 드디어 도착했어요!\n불빛 색깔이 사진보다 훨씬 예뻐요 💡"),
    ("{fandom} 1주년 축하해요", "벌써 우리가 함께한 지 이렇게 됐네요.\n앞으로도 오래오래 함께해요 🫶"),
    ("직캠 추천 부탁드려요", "입덕한 지 얼마 안 됐는데 꼭 봐야 하는 직캠 추천해 주세요!"),
    ("오늘 날씨 좋아서 앨범 들고 산책", "햇살 좋은 날 듣는 {name} 노래 최고예요 🌤️"),
    ("팬레터 쓰는 중 ✉️", "다음 팬사인회 때 드릴 편지 쓰고 있어요.\n무슨 말을 써야 할지 설레요 ㅎㅎ"),
    ("직접 그린 팬아트 🎨", "주말 동안 그려봤어요! 부족하지만 예쁘게 봐주세요 😊"),
]
COMMENTS = [
    "완전 공감해요!! 👍", "와 진짜 최고예요 😍", "오늘도 힐링하고 갑니다 💕", "저도 같은 생각이에요ㅋㅋ",
    "사진 너무 예뻐요 📸", "응원합니다!! 화이팅 🔥", "이거 보려고 하루 버텼어요", "저도 갈 거예요!!",
    "정보 감사합니다 🙏", "눈물 나요ㅠㅠ", "역시 믿고 보는 무대", "다음에도 꼭 올려주세요!",
]
ARTIST_REPLIES = ["고마워요 💕", "댓글 다 읽고 있어요!", "{fandom} 최고 👍", "다음에 또 만나요 😊"]

GOODS = [
    # 슬러그, 이름, 가격, 카테고리, 변형(옵션키, 값 목록), 멤버십 전용, 설명
    ("lightstick", "공식 응원봉 Ver.2", 45000, "OTHER", ("DEFAULT", [""]), False,
     "## 공식 응원봉 Ver.2\n\n- 블루투스 연동으로 공연장 연출과 함께 빛나요\n- AAA 건전지 3개 (별도 구매)\n- 전용 스트랩 포함"),
    ("hoodie", "로고 후드티", 69000, "CLOTHING", ("SIZE", ["S", "M", "L", "XL"]), False,
     "## 로고 후드티\n\n도톰한 기모 원단으로 겨울까지 따뜻하게!\n\n- 소재: 면 80%, 폴리 20%\n- 오버핏"),
    ("photocard", "포토카드 세트 (8종)", 15000, "OTHER", ("DEFAULT", [""]), False,
     "## 포토카드 세트\n\n멤버별 미공개 사진이 담긴 포토카드 8종 세트입니다."),
    ("keyring", "아크릴 키링", 12000, "ACCESSORY", ("DEFAULT", [""]), False,
     "## 아크릴 키링\n\n가방에 달기 좋은 귀여운 캐릭터 키링 🔑"),
    ("ecobag", "캔버스 에코백", 25000, "BAG", ("DEFAULT", [""]), False,
     "## 캔버스 에코백\n\n노트북까지 들어가는 넉넉한 사이즈의 에코백"),
    ("season", "2027 시즌그리팅 (멤버십 전용)", 38000, "OTHER", ("DEFAULT", [""]), True,
     "## 2027 시즌그리팅\n\n**멤버십 회원만 구매할 수 있는 한정 상품입니다.**\n\n- 탁상 달력\n- 다이어리\n- 포토북"),
]

MEDIA = [
    ("뮤직비디오 비하인드 컷", "촬영 현장에서 찍은 비하인드 사진을 공개합니다 🎬", 3, False),
    ("음악방송 출근길", "오늘도 출근길 응원 와주셔서 감사합니다!", 2, False),
    ("앨범 재킷 촬영 현장", "새 앨범 재킷 촬영 현장 스케치 📷", 3, False),
    ("[MEMBERSHIP] 미공개 셀카", "멤버십 회원에게만 공개하는 미공개 셀카 💌", 2, True),
    ("팬미팅 현장 스케치", "함께해 주신 모든 분들께 감사드려요!", 2, False),
]

SCHEDULES = [
    ("TV_BROADCAST", "음악중심 출연", "MBC 상암 공개홀", -2),
    ("YOUTUBE", "공식 유튜브 자체 콘텐츠 공개", None, 0),
    ("RADIO", "라디오 게스트 출연", "SBS 목동", 2),
    ("CONCERT", "단독 콘서트", "올림픽공원 KSPO DOME", 12),
    ("PHOTO_MAGAZINE", "패션 매거진 화보 공개", None, 20),
]


# ============================================================
# SQL 도우미
# ============================================================
def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def ago(minutes):
    return f"DATE_SUB(NOW(6), INTERVAL {int(minutes)} MINUTE)"


def later(minutes):
    return f"DATE_ADD(NOW(6), INTERVAL {int(minutes)} MINUTE)"


class Sql:
    def __init__(self):
        self.lines = []

    def comment(self, text):
        self.lines.append("")
        self.lines.append("-- " + "-" * 60)
        self.lines.append("-- " + text)
        self.lines.append("-- " + "-" * 60)

    def insert(self, table, columns, rows, raw_columns=()):
        """rows: 값 튜플 목록. raw_columns 에 있는 컬럼 값은 SQL 식 그대로 넣는다 (NOW() 등)"""
        if not rows:
            return
        cols = ", ".join(f"`{c}`" for c in columns)
        for chunk_start in range(0, len(rows), 50):
            chunk = rows[chunk_start:chunk_start + 50]
            values = []
            for row in chunk:
                parts = [v if c in raw_columns else q(v) for c, v in zip(columns, row)]
                values.append("  (" + ", ".join(parts) + ")")
            self.lines.append(f"INSERT INTO `{table}` ({cols}) VALUES\n" + ",\n".join(values) + ";")

    def raw(self, text):
        self.lines.append(text)


# ============================================================
# 이미지 도우미
# ============================================================
def font(size, weight="bold"):
    index = {"bold": 6, "semibold": 4, "medium": 2, "regular": 0}[weight]
    return ImageFont.truetype(FONT_PATH, size, index=index)


def mix(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def gradient(w, h, c1, c2, diagonal=True):
    img = Image.new("RGB", (w, h), c1)
    px = img.load()
    for y in range(h):
        for x in range(0, w, 2):
            t = ((x / w) + (y / h)) / 2 if diagonal else y / h
            c = mix(c1, c2, t)
            px[x, y] = c
            if x + 1 < w:
                px[x + 1, y] = c
    return img


def bokeh(img, color, count=18, seed=0):
    rnd = random.Random(seed)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    w, h = img.size
    for _ in range(count):
        r = rnd.randint(int(min(w, h) * 0.03), int(min(w, h) * 0.16))
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        alpha = rnd.randint(30, 90)
        d.ellipse([x - r, y - r, x + r, y + r], fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius=min(w, h) * 0.012))
    base = img.convert("RGBA")
    base.alpha_composite(layer)
    return base.convert("RGB")


def text_center(draw, box, text, fnt, fill=(255, 255, 255)):
    x0, y0, x1, y1 = box
    bb = draw.textbbox((0, 0), text, font=fnt)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    draw.text((x0 + (x1 - x0 - tw) / 2 - bb[0], y0 + (y1 - y0 - th) / 2 - bb[1]), text, font=fnt, fill=fill)


def fit_font(draw, text, max_w, start, weight="bold"):
    size = start
    while size > 12:
        f = font(size, weight)
        bb = draw.textbbox((0, 0), text, font=f)
        if bb[2] - bb[0] <= max_w:
            return f
        size -= 2
    return font(12, weight)


def save(img, name):
    path = os.path.join(IMG_DIR, name)
    if name.endswith(".png"):
        img.save(path, "PNG", optimize=True)
    else:
        img.save(path, "JPEG", quality=80, optimize=True, progressive=True)
    return os.path.getsize(path)


def make_logo(c, name):
    s = 512
    img = gradient(s, s, c["color"], c["color2"])
    img = bokeh(img, (255, 255, 255), 10, seed=len(name))
    d = ImageDraw.Draw(img)
    d.ellipse([56, 56, s - 56, s - 56], outline=(255, 255, 255), width=10)
    text_center(d, (0, 0, s, s), c["name"], fit_font(d, c["name"], s - 170, 120))
    return save(img, name)


def make_header(c, name):
    w, h = 1500, 500
    img = gradient(w, h, c["color"], c["color2"])
    img = bokeh(img, (255, 255, 255), 26, seed=w + len(c["slug"]))
    # 커뮤니티 화면이 헤더 위에 이름을 따로 그리므로 이미지에는 글자를 넣지 않는다
    return save(img, name)


def make_avatar(c, member_name, idx, name):
    s = 400
    c1 = mix(c["color"], (255, 255, 255), 0.12 * (idx % 3))
    c2 = mix(c["color2"], (0, 0, 0), 0.1 * (idx % 2))
    img = gradient(s, s, c1, c2)
    img = bokeh(img, (255, 255, 255), 8, seed=idx * 7 + s)
    d = ImageDraw.Draw(img)
    text_center(d, (0, 0, s, s - 40), member_name, fit_font(d, member_name, s - 80, 130))
    text_center(d, (0, s - 120, s, s - 40), c["name"], font(34, "medium"))
    return save(img, name)


def draw_product(d, kind, cx, cy, s, color, accent):
    white = (255, 255, 255)
    if kind == "lightstick":
        d.rounded_rectangle([cx - s * 0.06, cy - s * 0.05, cx + s * 0.06, cy + s * 0.42], radius=int(s * 0.04), fill=(60, 60, 70))
        d.ellipse([cx - s * 0.22, cy - s * 0.42, cx + s * 0.22, cy + s * 0.02], fill=accent)
        d.ellipse([cx - s * 0.14, cy - s * 0.34, cx + s * 0.14, cy - s * 0.06], fill=white)
    elif kind == "hoodie":
        d.polygon([(cx - s * 0.36, cy - s * 0.18), (cx - s * 0.14, cy - s * 0.36), (cx + s * 0.14, cy - s * 0.36),
                   (cx + s * 0.36, cy - s * 0.18), (cx + s * 0.28, cy - s * 0.02), (cx + s * 0.22, cy - s * 0.08),
                   (cx + s * 0.22, cy + s * 0.38), (cx - s * 0.22, cy + s * 0.38), (cx - s * 0.22, cy - s * 0.08),
                   (cx - s * 0.28, cy - s * 0.02)], fill=color)
        d.ellipse([cx - s * 0.1, cy - s * 0.42, cx + s * 0.1, cy - s * 0.26], fill=accent)
        d.rounded_rectangle([cx - s * 0.12, cy + s * 0.12, cx + s * 0.12, cy + s * 0.26], radius=10, fill=accent)
    elif kind == "photocard":
        for i, off in enumerate([-0.12, -0.04, 0.04]):
            d.rounded_rectangle([cx - s * 0.2 + s * off, cy - s * 0.3 + s * off, cx + s * 0.2 + s * off, cy + s * 0.3 + s * off],
                                radius=18, fill=mix(color, white, 0.2 * i), outline=white, width=6)
    elif kind == "keyring":
        d.ellipse([cx - s * 0.1, cy - s * 0.42, cx + s * 0.1, cy - s * 0.22], outline=(160, 160, 170), width=12)
        d.rounded_rectangle([cx - s * 0.22, cy - s * 0.18, cx + s * 0.22, cy + s * 0.3], radius=40, fill=color)
        d.ellipse([cx - s * 0.08, cy - s * 0.02, cx + s * 0.08, cy + s * 0.14], fill=accent)
    elif kind == "ecobag":
        d.arc([cx - s * 0.16, cy - s * 0.42, cx + s * 0.16, cy - s * 0.1], 180, 360, fill=(120, 110, 90), width=14)
        d.rectangle([cx - s * 0.28, cy - s * 0.24, cx + s * 0.28, cy + s * 0.38], fill=(238, 228, 205))
        d.ellipse([cx - s * 0.1, cy - s * 0.02, cx + s * 0.1, cy + s * 0.18], fill=color)
    elif kind == "season":
        d.polygon([(cx - s * 0.3, cy + s * 0.36), (cx - s * 0.2, cy - s * 0.3), (cx + s * 0.2, cy - s * 0.3), (cx + s * 0.3, cy + s * 0.36)], fill=(70, 70, 80))
        d.rectangle([cx - s * 0.22, cy - s * 0.26, cx + s * 0.22, cy + s * 0.3], fill=white)
        d.rectangle([cx - s * 0.22, cy - s * 0.26, cx + s * 0.22, cy - s * 0.12], fill=color)
        for r in range(3):
            for col in range(4):
                x = cx - s * 0.17 + col * s * 0.11
                y = cy - s * 0.06 + r * s * 0.11
                d.rectangle([x, y, x + s * 0.07, y + s * 0.07], fill=mix(color, white, 0.6))


def make_goods(c, kind, title, price, name):
    s = 800
    img = Image.new("RGB", (s, s), mix(c["color"], (255, 255, 255), 0.88))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([40, 40, s - 40, s - 40], radius=40, fill=(255, 255, 255))
    draw_product(d, kind, s / 2, s * 0.42, s * 0.8, c["color"], c["color2"])
    d.text((80, s - 190), c["name"], font=font(34, "semibold"), fill=c["color"])
    d.text((80, s - 145), title, font=fit_font(d, title, s - 160, 44), fill=(30, 30, 40))
    return save(img, name)


def make_photo(c, caption, seed, name, size=(1080, 1080), with_caption=True):
    w, h = size
    rnd = random.Random(seed)
    c1 = mix(c["color"], (0, 0, 0), rnd.uniform(0.1, 0.45))
    c2 = mix(c["color2"], (255, 255, 255), rnd.uniform(0, 0.25))
    img = gradient(w, h, c1, c2, diagonal=rnd.random() < 0.5)
    img = bokeh(img, (255, 255, 255), 28, seed=seed)
    img = bokeh(img, mix(c["color2"], (255, 255, 255), 0.5), 12, seed=seed + 1)
    if not with_caption:
        return save(img, name)
    d = ImageDraw.Draw(img)
    d.rectangle([0, h - 150, w, h], fill=(0, 0, 0))
    d.text((48, h - 118), caption, font=fit_font(d, caption, w - 96, 48, "semibold"), fill=(255, 255, 255))
    d.text((w - 220, 40), c["name"], font=font(40, "bold"), fill=(255, 255, 255))
    return save(img, name)


def make_banner(c, name):
    # 메인 배너는 제목/본문을 화면이 따로 그리므로 이미지에는 글자를 넣지 않는다
    w, h = 1600, 900
    img = gradient(w, h, c["color"], c["color2"])
    img = bokeh(img, (255, 255, 255), 34, seed=w + c["id"])
    img = bokeh(img, mix(c["color2"], (255, 255, 255), 0.5), 14, seed=w + c["id"] + 1)
    return save(img, name)


# ============================================================
# 생성
# ============================================================
def main():
    if os.path.isdir(IMG_DIR):
        shutil.rmtree(IMG_DIR)
    os.makedirs(IMG_DIR)

    sql = Sql()
    image_count = 0
    image_bytes = 0

    def img(size_bytes):
        nonlocal image_count, image_bytes
        image_count += 1
        image_bytes += size_bytes
        return size_bytes

    # ---------------- 소속사 + 담당자 ----------------
    sql.comment("[1] 소속사 3곳 + 소속사 담당자 계정 (비밀번호 공통 Test1234)")
    sql.insert("agencies", ["id", "name", "business_no", "ceo_name", "status", "created_at", "updated_at"],
               [(a[0], a[1], a[2], a[3], "ACTIVE", ago(60 * 24 * 400), ago(60 * 24 * 400)) for a in AGENCIES],
               raw_columns=("created_at", "updated_at"))
    user_cols = ["id", "username", "password", "role", "status", "agency_id", "real_name", "nickname", "email",
                 "email_verified_at", "created_at", "updated_at"]
    raw_user = ("email_verified_at", "created_at", "updated_at")
    sql.insert("users", user_cols,
               [(AGENCY_USER_ID[a[0]], a[4], PW, "AGENCY", "ACTIVE", a[0], a[3], a[5], f"{a[4]}@weplanet.test",
                 ago(60 * 24 * 400), ago(60 * 24 * 400), ago(60 * 24 * 400)) for a in AGENCIES], raw_columns=raw_user)
    sql.insert("agency_profiles", ["user_id", "agency_id", "department", "position", "is_owner"],
               [(AGENCY_USER_ID[a[0]], a[0], "매니지먼트팀", "팀장", True) for a in AGENCIES])

    # ---------------- 최고관리자 ----------------
    sql.comment("[2] 최고관리자 4명 - 인증번호는 전부 admin4.wp@gmail.com 메일함으로 온다 (+별칭)")
    sql.insert("users", user_cols,
               [(a[0], a[1], PW, "ADMIN", "ACTIVE", None, a[2], a[2], a[3], ago(60 * 24 * 300), ago(60 * 24 * 300),
                 ago(60 * 24 * 300)) for a in ADMINS], raw_columns=raw_user)
    sql.insert("admin_profiles", ["user_id", "admin_level", "department", "employee_no"],
               [(a[0], "SUPER", "플랫폼운영팀", a[4]) for a in ADMINS])
    sql.raw("-- 기존 시드의 admin_test 도 실제로 받을 수 있는 주소로 맞춘다")
    sql.raw("UPDATE `users` SET `email` = 'admin4.wp@gmail.com' WHERE `username` = 'admin_test';")

    # ---------------- 커뮤니티(아티스트) + 멤버 ----------------
    sql.comment("[3] 커뮤니티 6개 (그룹 4 + 솔로 2) - 아티스트 계정, 그룹 정보, 멤버, 포털 프로필")
    member_rows, ap_rows, group_rows, gm_rows, portal_rows = [], [], [], [], []
    community_users = []
    next_member_id = 1201
    for c in COMMUNITIES:
        days_since_debut = 900
        logo = f"demo_{c['slug']}_logo.png"
        header = f"demo_{c['slug']}_header.jpg"
        img(make_logo(c, logo))
        img(make_header(c, header))
        c["logo"], c["header"] = logo, header
        community_users.append((c["id"], c["username"], PW, "ARTIST", "ACTIVE", c["agency"], c["name"], c["name"],
                                f"{c['username']}@weplanet.test", ago(60 * 24 * days_since_debut),
                                ago(60 * 24 * days_since_debut), ago(60 * 24 * 10)))
        ap_rows.append((c["id"], c["agency"], c["name"], c["debut"], "GROUP" if c["members"] else "SOLO",
                        c["intro"], f"/uploads/{logo}"))
        member_count = len(c["members"]) or 1
        group_rows.append((c["id"], c["agency"], c["name"], c["name_en"], c["fandom"], c["debut"], "ACTIVE",
                           c["gender"], member_count, c["nationality"], c["category"], ago(60 * 24 * 900), ago(60 * 24 * 10)))
        portal_rows.append((c["id"], c["intro"], header, logo))

        c["member_ids"] = []
        for i, (mname, position) in enumerate(c["members"]):
            mid = next_member_id
            next_member_id += 1
            uname = f"member_{c['id']}_{c['slug']}{i + 1}"
            avatar = f"demo_{c['slug']}_member{i + 1}.png"
            img(make_avatar(c, mname, i, avatar))
            member_rows.append((mid, uname, PW, "ARTIST_MEMBER", "ACTIVE", c["agency"], mname, mname,
                                f"{uname}@member.weplanet.local", ago(60 * 24 * 900), ago(60 * 24 * 900), ago(60 * 24 * 5)))
            ap_rows.append((mid, c["agency"], mname, c["debut"], position, f"{c['name']}의 {mname}입니다!",
                            f"/uploads/{avatar}"))
            gm_rows.append((c["id"], mid, i == 0, c["debut"]))
            portal_rows.append((mid, f"{c['name']} {mname} 💫", None, avatar))
            c["member_ids"].append(mid)
        # DM/게시글 작성자 후보: 그룹이면 멤버들, 솔로면 본인
        c["writers"] = c["member_ids"] or [c["id"]]

    sql.insert("users", user_cols, community_users, raw_columns=raw_user)
    sql.raw("-- 그룹 멤버 계정: 그룹 계정으로 로그인 -> 프로필 선택 -> 개인 비밀번호(Test1234)")
    sql.insert("users", user_cols, member_rows, raw_columns=raw_user)
    sql.insert("artist_profiles", ["user_id", "agency_id", "stage_name", "debut_date", "position", "bio", "profile_img"], ap_rows)
    sql.insert("artist_groups", ["id", "agency_id", "name", "name_en", "fandom_name", "debut_date", "status", "gender",
                                 "member_count", "nationality", "category", "created_at", "updated_at"],
               group_rows, raw_columns=("created_at", "updated_at"))
    sql.insert("group_members", ["group_id", "artist_id", "is_leader", "joined_at"], gm_rows)
    sql.insert("artist_profile", ["artist_id", "intro", "header_image_url", "logo_image_url", "created_at", "updated_at"],
               [r + ("NOW()", "NOW()") for r in portal_rows], raw_columns=("created_at", "updated_at"))

    # ---------------- 팬 ----------------
    sql.comment("[4] 팬 15명 + 커뮤니티 가입(커뮤니티별 프로필) + 멤버십 + 팔로우")
    fan_rows = []
    for i, (uname, nick, _) in enumerate(FANS):
        fid = FAN_ID0 + i
        fan_rows.append((fid, uname, PW, "FAN", "ACTIVE", None, nick, nick, f"{uname}@weplanet.test",
                         ago(60 * 24 * (200 - i * 5)), ago(60 * 24 * (200 - i * 5)), ago(60 * 24 * 2)))
    sql.insert("users", user_cols, fan_rows, raw_columns=raw_user)

    cm_rows, ms_rows, mp_rows, follow_rows = [], [], [], []
    members_of = {c["id"]: [] for c in COMMUNITIES}   # 커뮤니티별 가입 팬
    paid_members_of = {c["id"]: [] for c in COMMUNITIES}
    for fi, joins in FAN_JOINS.items():
        fid = FAN_ID0 + fi
        for k, ci in enumerate(joins):
            c = COMMUNITIES[ci]
            joined_days = 150 - fi * 4 - k * 9
            bio = random.choice(["오늘도 행복한 덕질 💕", "늘 응원해요!", "입덕 부정기 끝 🥹", "콘서트 꼭 갈래요", None])
            cm_rows.append((fid, c["id"], ago(60 * 24 * joined_days), FANS[fi][2], bio, ago(60 * 24 * 3)))
            members_of[c["id"]].append(fid)
            follow_rows.append((fid, c["id"], c["id"], ago(60 * 24 * joined_days)))
            # QA데모팬(15번)은 모든 커뮤니티 멤버십 보유, 나머지는 2/3 정도
            if fi == 14 or (fi + ci) % 3 != 0:
                started = joined_days - 2
                ms_rows.append((ago(60 * 24 * started), f"DATE_ADD({ago(60 * 24 * started)}, INTERVAL 1 YEAR)", c["id"], fid))
                mp_rows.append((fid, c["id"], ago(60 * 24 * started), f"DATE_ADD({ago(60 * 24 * started)}, INTERVAL 1 YEAR)",
                                1, ago(60 * 24 * started)))
                paid_members_of[c["id"]].append(fid)
    sql.insert("community_members", ["fan_id", "artist_id", "joined_at", "nickname", "bio", "updated_at"], cm_rows,
               raw_columns=("joined_at", "updated_at"))
    sql.insert("membership", ["created_at", "expires_at", "artist_id", "fan_id"], ms_rows,
               raw_columns=("created_at", "expires_at"))
    sql.insert("membership_period", ["fan_id", "artist_id", "started_at", "expires_at", "streak_count", "created_at"],
               mp_rows, raw_columns=("started_at", "expires_at", "created_at"))
    # 같은 커뮤니티 팬끼리 팔로우 몇 쌍
    for c in COMMUNITIES:
        fans = members_of[c["id"]]
        for a, b in zip(fans, fans[1:] + fans[:1]):
            if a != b:
                follow_rows.append((a, b, c["id"], ago(60 * 24 * 7)))
    follow_rows = list({(r[0], r[1], r[2]): r for r in follow_rows}.values())
    sql.insert("user_follows", ["follower_id", "following_id", "community_id", "created_at"], follow_rows,
               raw_columns=("created_at",))

    # ---------------- 게시글 / 첨부 / 댓글 / 좋아요 ----------------
    sql.comment("[5] 게시글 (아티스트 게시판 + 팬 게시판) / 첨부 이미지 / 댓글 / 좋아요")
    post_rows, att_rows, comment_rows, like_rows = [], [], [], []
    post_id, att_id, comment_id = 10001, 10001, 20001
    for c in COMMUNITIES:
        fans = members_of[c["id"]]
        fmt = dict(name=c["name"], fandom=c["fandom"])
        entries = []
        for i, (title, body) in enumerate(ARTIST_POSTS[:6]):
            entries.append(("ARTIST", c["writers"][i % len(c["writers"])], title, body, i % 2 == 0))
        for i, (title, body) in enumerate(FAN_POSTS[:12]):
            entries.append(("FAN", fans[i % len(fans)], title, body, i % 4 == 0))
        random.shuffle(entries)
        for n, (board, author, title, body, with_image) in enumerate(entries):
            minutes = 60 * (6 + n * 37) + random.randint(0, 50)
            likers = random.sample(fans, k=random.randint(0, len(fans)))
            post_rows.append((post_id, board, c["id"], body.format(**fmt), ago(minutes), title.format(**fmt), author,
                              len(likers), False))
            for u in likers:
                like_rows.append((ago(minutes - 5), post_id, u))
            if with_image:
                stored = f"demo_{c['slug']}_post{post_id}.jpg"
                size = img(make_photo(c, title.format(**fmt), post_id, stored))
                att_rows.append((att_id, "image/jpeg", ago(minutes), size, f"photo_{n + 1}.jpg", stored, post_id))
                att_id += 1
            # 댓글: 팬 2~4개 + 아티스트 글이면 작성자 답글 하나
            parent_for_reply = None
            for k in range(random.randint(1, 4)):
                commenter = random.choice(fans)
                comment_rows.append((comment_id, random.choice(COMMENTS), ago(minutes - 10 - k * 7), commenter, post_id, None))
                parent_for_reply = parent_for_reply or comment_id
                comment_id += 1
            if board == "ARTIST" and parent_for_reply:
                comment_rows.append((comment_id, random.choice(ARTIST_REPLIES).format(**fmt), ago(minutes - 40), author,
                                     post_id, parent_for_reply))
                comment_id += 1
            post_id += 1
    sql.insert("post", ["id", "board_type", "artist_id", "content", "created_at", "title", "author_id", "like_count",
                        "hidden_from_artist"], post_rows, raw_columns=("created_at",))
    sql.insert("post_attachment", ["id", "content_type", "created_at", "file_size", "original_name", "stored_name", "post_id"],
               att_rows, raw_columns=("created_at",))
    sql.insert("comment", ["id", "content", "created_at", "author_id", "post_id", "parent_id"], comment_rows,
               raw_columns=("created_at",))
    sql.insert("post_like", ["created_at", "post_id", "user_id"], like_rows, raw_columns=("created_at",))

    # ---------------- 미디어 ----------------
    sql.comment("[6] 미디어 게시판 (소속사 업로드) / 이미지 / 좋아요")
    media_rows, file_rows, media_like_rows = [], [], []
    media_id, file_id = 3001, 3001
    for c in COMMUNITIES:
        fans = members_of[c["id"]]
        for n, (title, content, count, membership_only) in enumerate(MEDIA):
            minutes = 60 * 24 * (n * 4 + 1) + random.randint(0, 300)
            likers = random.sample(fans, k=random.randint(1, len(fans)))
            media_rows.append((media_id, c["id"], AGENCY_USER_ID[c["agency"]], title, content, ago(minutes), ago(minutes),
                               len(likers), membership_only))
            for u in likers:
                media_like_rows.append((media_id, u, ago(minutes - 30)))
            for k in range(count):
                stored = f"demo_{c['slug']}_media{media_id}_{k + 1}.jpg"
                size = img(make_photo(c, f"{title} #{k + 1}", media_id * 10 + k, stored, size=(1280, 720)))
                file_rows.append((file_id, media_id, f"{c['slug']}_{n + 1}_{k + 1}.jpg", stored, "image/jpeg", "IMAGE",
                                  size, k, ago(minutes)))
                file_id += 1
            media_id += 1
    sql.insert("board_media", ["id", "group_id", "uploader_id", "title", "content", "created_at", "updated_at",
                               "like_count", "membership_only"], media_rows, raw_columns=("created_at", "updated_at"))
    sql.insert("board_media_files", ["id", "board_id", "original_name", "stored_name", "content_type", "media_type",
                                     "file_size", "sort_order", "created_at"], file_rows, raw_columns=("created_at",))
    sql.insert("board_media_like", ["board_id", "user_id", "created_at"], media_like_rows, raw_columns=("created_at",))

    # ---------------- 굿즈 ----------------
    sql.comment("[7] 굿즈샵 - 커뮤니티마다 6개 (멤버십 전용 1개 포함)")
    goods_rows, cat_rows, var_rows = [], [], []
    goods_id = 4001
    goods_by_slug = {}
    for ci, c in enumerate(COMMUNITIES):
        for n, (kind, title, price, category, (opt_key, values), membership_only, desc) in enumerate(GOODS):
            thumb = f"demo_{c['slug']}_goods_{kind}.jpg"
            img(make_goods(c, kind, title, price, thumb))
            goods_rows.append((goods_id, c["id"], f"{c['name']} {title}", desc, price, thumb, None, "ON_SALE", (n + ci) % len(GOODS),
                               membership_only, "MEMBERSHIP" if membership_only else "MD", ago(60 * 24 * (30 - n)),
                               ago(60 * 24 * (30 - n))))
            cat_rows.append((goods_id, category))
            for v in values:
                var_rows.append((goods_id, opt_key, v, random.choice([30, 50, 80, 120])))
            goods_by_slug[(c["slug"], kind)] = goods_id
            goods_id += 1
    sql.insert("shop_goods", ["id", "artist_id", "name", "description", "price", "thumbnail_url", "official_url", "status",
                              "sort_order", "membership_only", "shop_category", "created_at", "updated_at"], goods_rows,
               raw_columns=("created_at", "updated_at"))
    sql.insert("shop_goods_category", ["goods_id", "category"], cat_rows)
    sql.insert("shop_goods_variant", ["goods_id", "option_key", "option_value", "stock_quantity"], var_rows)

    # ---------------- 일정 / 공지 ----------------
    sql.comment("[8] 아티스트 일정 / 커뮤니티 공지(포털) / 홈페이지 공지")
    sched_rows, pnotice_rows = [], []
    for ci, c in enumerate(COMMUNITIES):
        for n, (cat, title, location, days) in enumerate(SCHEDULES):
            at = f"DATE_ADD(DATE(NOW()), INTERVAL {days + ci % 3} DAY) + INTERVAL {18 + (n % 3)} HOUR"
            sched_rows.append((c["id"], cat, f"{c['name']} {title}", f"{c['fandom']} 여러분 많은 관심 부탁드려요!",
                               location, None, at, "NOW()", "NOW()"))
        pnotice_rows.append((c["id"], f"[공지] {c['name']} 커뮤니티 이용 안내",
                             f"{c['fandom']} 여러분 안녕하세요!\n\n서로를 존중하는 따뜻한 커뮤니티를 만들어 주세요.\n"
                             "- 비방, 욕설 금지\n- 개인정보 공유 금지\n- 불법 촬영물 공유 금지", True, True, 1, "NOW()", "NOW()"))
        pnotice_rows.append((c["id"], f"[이벤트] 컴백 기념 응원 댓글 이벤트",
                             "컴백을 기념해 응원 댓글을 남겨주신 분들 중 추첨을 통해 사인 앨범을 드립니다 🎁",
                             True, False, None, "NOW()", "NOW()"))
    sql.insert("artist_schedule", ["artist_id", "category", "title", "description", "location", "ticket_url",
                                   "schedule_at", "created_at", "updated_at"], sched_rows,
               raw_columns=("schedule_at", "created_at", "updated_at"))
    sql.insert("portal_notice", ["artist_id", "title", "content", "published", "pinned", "pin_order", "created_at",
                                 "updated_at"], pnotice_rows, raw_columns=("created_at", "updated_at"))
    sql.insert("site_notice", ["author_id", "title", "category", "content", "published", "pinned", "pin_order",
                               "created_at", "updated_at"], [
        (1001, "WePlaNet 정식 오픈 안내 🎉", "GENERAL", "아티스트와 팬이 함께하는 공간, WePlaNet 이 정식 오픈했습니다!\n\n많은 이용 부탁드립니다.", True, True, 1, ago(60 * 24 * 20), ago(60 * 24 * 20)),
        (1001, "가을맞이 굿즈 기획전 오픈", "EVENT", "커뮤니티별 신상 굿즈를 만나보세요. 기간 한정 무료배송 이벤트도 진행 중입니다.", True, False, None, ago(60 * 24 * 6), ago(60 * 24 * 6)),
        (1002, "서비스 점검 안내 (새벽 2시~4시)", "MAINTENANCE", "보다 안정적인 서비스를 위해 서버 점검을 진행합니다.\n점검 시간에는 접속이 원활하지 않을 수 있습니다.", True, False, None, ago(60 * 24 * 3), ago(60 * 24 * 3)),
        (1003, "DM 기능 업데이트: 멤버별 1:1 대화", "GENERAL", "이제 그룹 멤버 한 명 한 명과 DM 을 나눌 수 있어요 💌", True, False, None, ago(60 * 24 * 1), ago(60 * 24 * 1)),
    ], raw_columns=("created_at", "updated_at"))

    # ---------------- 메인 배너 ----------------
    sql.comment("[9] 메인 배너 4개 (커뮤니티 홍보 2 + 상품 홍보 2)")
    banner_rows = []
    banners = [
        (COMMUNITIES[0], "COMMUNITY", None, "NOVA 정규 2집 컴백", "스텔라와 함께하는 새로운 여정", "#4F46E5", "#FFFFFF"),
        (COMMUNITIES[4], "COMMUNITY", None, "한유리 단독 콘서트", "작은 방에서 큰 무대로", "#F59E0B", "#1F2937"),
        (COMMUNITIES[1], "PRODUCT", goods_by_slug[("lumi", "hoodie")], "LUMI 로고 후드티", "가을 신상 굿즈 오픈", "#EC4899", "#FFFFFF"),
        (COMMUNITIES[2], "PRODUCT", goods_by_slug[("eclipse", "lightstick")], "ECLIPSE 응원봉 Ver.2", "공연장을 밝히는 단 하나의 빛", "#1E293B", "#FFFFFF"),
    ]
    for n, (c, btype, gid, title, body, bg, fg) in enumerate(banners):
        stored = f"demo_banner_{n + 1}_{c['slug']}.jpg"
        img(make_banner(c, stored))
        banner_rows.append((btype, c["id"], gid, title, body, stored, bg, fg, True, n, 1001, "NOW(6)", "NOW(6)"))
    sql.insert("main_banner", ["banner_type", "artist_id", "goods_id", "title", "body", "image_stored_name", "bg_color",
                               "text_color", "active", "sort_order", "created_by", "created_at", "updated_at"],
               banner_rows, raw_columns=("created_at", "updated_at"))

    # ---------------- 팬 프로젝트 ----------------
    sql.comment("[10] 팬 프로젝트 2개 (모금 중) + 모의 결제(MOCK) 후원 내역. 정산 계좌는 앱 암호화가 필요해 시드에서 제외")
    projects = [
        (501, COMMUNITIES[0], FAN_ID0 + 0, "NOVA 데뷔 4주년 지하철광고", "BILLBOARD", 1500000,
         "스텔라가 함께 준비하는 NOVA 데뷔 4주년 지하철 광고 프로젝트입니다 🚇\n모인 금액은 광고 제작 및 게재 비용으로 사용됩니다."),
        (502, COMMUNITIES[1], FAN_ID0 + 2, "서아 생일카페 프로젝트", "BIRTHDAY_CAFE", 800000,
         "LUMI 서아의 생일을 맞아 생일카페를 엽니다 🎂\n컵홀더, 포토존, 특전 굿즈를 준비할 예정이에요."),
    ]
    proj_rows, cover_rows, contrib_rows = [], [], []
    for pid, c, creator, title, etype, goal, desc in projects:
        proj_rows.append((pid, c["id"], creator, title, etype, goal, ago(60 * 24 * 5), later(60 * 24 * 20), desc,
                          "FUNDING", 1, 5, ago(60 * 24 * 7), 1001, ago(60 * 24 * 6), ago(60 * 24 * 7), ago(60 * 24 * 5)))
        stored = f"demo_project_{pid}.jpg"
        size = img(make_photo(c, title, pid, stored, size=(1200, 800), with_caption=False))
        cover_rows.append((pid, f"cover_{pid}.jpg", stored, "image/jpeg", size, ago(60 * 24 * 7)))
        for k, fid in enumerate(paid_members_of[c["id"]][:6]):
            amount = random.choice([10000, 20000, 30000, 50000, 100000])
            minutes = 60 * (100 - k * 13)
            contrib_rows.append((pid, fid, f"DEMO-{pid}-{k + 1:03d}", f"demo-{pid}-{k + 1:03d}", "MOCK", amount,
                                 k % 3 == 0, ago(minutes), "PAID", ago(minutes), ago(minutes), ago(minutes)))
    sql.insert("fan_project", ["id", "artist_id", "creator_id", "title", "event_type", "goal_amount", "funding_start_at",
                               "funding_end_at", "description", "status", "special_badge_count_at_apply",
                               "basic_badge_count_at_apply", "identity_verified_at", "reviewed_by", "reviewed_at",
                               "created_at", "updated_at"], proj_rows,
               raw_columns=("funding_start_at", "funding_end_at", "identity_verified_at", "reviewed_at", "created_at", "updated_at"))
    sql.insert("fan_project_cover_image", ["project_id", "original_name", "stored_name", "content_type", "file_size",
                                           "created_at"], cover_rows, raw_columns=("created_at",))
    sql.insert("fan_project_contribution", ["project_id", "contributor_id", "order_no", "idempotency_key",
                                            "payment_provider", "amount", "is_anonymous", "refund_policy_agreed_at",
                                            "payment_status", "paid_at", "created_at", "updated_at"], contrib_rows,
               raw_columns=("refund_policy_agreed_at", "paid_at", "created_at", "updated_at"))

    # ---------------- DM ----------------
    sql.comment("[11] DM - 멤버별 방송 메시지 + 멤버십 팬이 보낸 1:1 메시지 (멤버별 DM 코드와 짝)")
    chat_rows = []
    greetings = ["{fandom} 오늘 하루 어땠어요? 💬", "방금 연습 끝났어요! 다들 뭐 해요?", "자기 전에 인사하러 왔어요 🌙"]
    fan_msgs = ["오늘 무대 너무 멋있었어요!!", "답장해줘서 고마워요 😭", "밥 꼭 챙겨 먹어요!", "다음 콘서트 꼭 갈게요 💜"]
    for c in COMMUNITIES:
        fmt = dict(fandom=c["fandom"])
        for w in c["writers"]:
            for k, g in enumerate(greetings[:2]):
                chat_rows.append((g.format(**fmt), ago(60 * (30 - k * 5) + w % 7), w, None, w, True))
            demo_fan = FAN_ID0 + len(FANS) - 1
            dm_fans = [f for f in paid_members_of[c["id"]] if f != demo_fan][:2] + [demo_fan]
            for k, fid in enumerate(dm_fans):
                chat_rows.append((random.choice(fan_msgs), ago(60 * (20 - k * 3) + w % 5), w, fid, fid, k != 2))
    sql.insert("chat_message", ["content", "created_at", "artist_id", "fan_id", "sender_id", "visible_to_artist"],
               chat_rows, raw_columns=("created_at",))

    # ---------------- 파일 쓰기 ----------------
    header = f"""-- ============================================================
-- WePlaNet 데모 데이터 (발표 / 테스트 배포용)  -  generate_demo_seed.py 로 생성됨, 직접 고치지 말고 스크립트를 고쳐서 다시 만들 것
-- ------------------------------------------------------------
-- 실행 순서
--   1) weplanet_schema_full_reset_v2.sql  (전체 초기화 + 기본 시드)
--   2) 이 파일                            (데모 데이터 추가)
--   3) docs/demo/demo_images/* 를 서버 실행 폴더의 uploads/ 에 복사  (이미지 {image_count}개)
--
-- 계정 (비밀번호 공통: Test1234)
--   최고관리자  admin_super1 ~ admin_super4   인증번호 -> admin4.wp@gmail.com 메일함 (+1 ~ +4 별칭)
--   소속사      agency_starlight / agency_bluewave / agency_moonbeam
--   그룹        nova_official / lumi_official / eclipse_official / prism_official
--               -> 로그인 후 멤버 프로필 선택, 멤버 개인 비밀번호도 Test1234
--   솔로        yuri_official / kaito_official
--   팬          demo_fan01 ~ demo_fan15   (demo_fan15 = 모든 커뮤니티 가입 + 멤버십 보유)
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;
"""
    footer = """
-- ------------------------------------------------------------
-- [확인] 데모 데이터 개수
-- ------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `users` WHERE `id` >= 1001)       AS demo_users,
  (SELECT COUNT(*) FROM `artist_groups` WHERE `id` >= 1101) AS communities,
  (SELECT COUNT(*) FROM `post` WHERE `id` >= 10001)       AS posts,
  (SELECT COUNT(*) FROM `shop_goods` WHERE `id` >= 4001)  AS goods,
  (SELECT COUNT(*) FROM `board_media` WHERE `id` >= 3001) AS media;
"""
    with open(SQL_PATH, "w", encoding="utf-8") as f:
        f.write(header + "\n".join(sql.lines) + "\n" + footer)

    print(f"SQL: {SQL_PATH}")
    print(f"images: {image_count} files, {image_bytes / 1024 / 1024:.1f} MB -> {IMG_DIR}")


if __name__ == "__main__":
    main()
