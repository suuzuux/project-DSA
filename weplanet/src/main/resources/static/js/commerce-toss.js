(function () {
  "use strict";

  function createIdempotencyKey() {
    if (window.crypto && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return "idemp-" + Date.now() + "-" + Math.random().toString(16).slice(2);
  }

  async function openVirtualAccount(prepared, successUrl, failUrl) {
    if (!prepared || !prepared.success || !prepared.clientKey) {
      throw new Error((prepared && prepared.message) || "결제 정보를 받지 못했습니다.");
    }
    if (!window.TossPayments) {
      throw new Error("결제 모듈을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
    var tossPayments = TossPayments(prepared.clientKey);
    var payment = tossPayments.payment({ customerKey: TossPayments.ANONYMOUS });
    await payment.requestPayment({
      method: "VIRTUAL_ACCOUNT",
      amount: { currency: "KRW", value: prepared.amount },
      orderId: prepared.orderId,
      orderName: prepared.orderName,
      customerName: prepared.customerName,
      successUrl: successUrl,
      failUrl: failUrl,
      virtualAccount: {
        cashReceipt: { type: "미발행" },
        useEscrow: false,
        validHours: prepared.validHours
      }
    });
  }

  async function postForm(url, fields) {
    var body = new FormData();
    Object.keys(fields || {}).forEach(function (key) {
      if (fields[key] != null) {
        body.append(key, String(fields[key]));
      }
    });
    var response = await fetch(url, {
      method: "POST",
      headers: { "X-Requested-With": "fetch", Accept: "application/json" },
      body: body
    });
    if (response.status === 401) {
      window.location.href = "/login";
      throw new Error("로그인이 필요합니다.");
    }
    var data = await response.json().catch(function () {
      return null;
    });
    if (!response.ok || !data || data.success === false) {
      throw new Error((data && data.message) || "결제 준비에 실패했습니다.");
    }
    return data;
  }

  window.WePlaNetToss = {
    createIdempotencyKey: createIdempotencyKey,
    openVirtualAccount: openVirtualAccount,
    postForm: postForm
  };
})();
