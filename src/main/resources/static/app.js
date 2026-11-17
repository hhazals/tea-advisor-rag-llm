"use strict";

const CONDITION_LABELS = {
    reflux: "Reflü",
    pregnancy: "Hamilelik / emzirme",
    lactose: "Laktoz hassasiyeti",
    asteraceae_allergy: "Papatyagil alerjisi",
    under_12: "12 yaş altı",
    under_18: "18 yaş altı",
    kidney: "Böbrek rahatsızlığı",
    anticoagulant: "Kan sulandırıcı ilaç",
};
const MEAL_LABELS = { breakfast: "Kahvaltı", lunch: "Öğle", dinner: "Akşam", snack: "Ara öğün" };
const SEVERITY_LABELS = { exclude: "uygun değil", caution: "dikkat", info: "bilgi" };

const $ = (sel) => document.querySelector(sel);

/** Yerel tarih (toISOString UTC'ye çevirir, gece yarısı kayar). */
function today() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Metinler her zaman textContent ile eklenir (LLM / kullanıcı metni HTML olarak yorumlanmaz). */
function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (k === "class") node.className = v;
        else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v);
    }
    for (const c of children.flat()) {
        if (c == null || c === false) continue;
        node.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return node;
}

async function api(path, options = {}) {
    const res = await fetch(path, {
        ...options,
        headers: options.body ? { "Content-Type": "application/json" } : {},
    });
    if (res.status === 204) return null;
    const body = await res.json().catch(() => null);
    if (!res.ok) throw new Error(body?.detail || `İstek başarısız (${res.status})`);
    return body;
}

function setStatus(target, text, isError = false) {
    const node = $(target);
    node.textContent = text;
    node.classList.toggle("error", isError);
}

// ---------- 1) Yemek ekleme (serbest metin → Gemini ayrıştırır) ----------

function itemsPayload(meal) {
    return meal.items.map((i) => ({ foodId: i.foodId, portionGram: i.portionGram }));
}

/** Saate göre varsayılan öğün. */
function defaultMealType() {
    const h = new Date().getHours();
    return h < 11 ? "breakfast" : h < 16 ? "lunch" : "dinner";
}

function renderParsed(items) {
    $("#parsed").replaceChildren(...items.map((p) => el("li", {},
        el("span", {},
            `${p.nameTr} → ${p.nameEn}`,
            el("span", { class: "muted" },
                ` · ${p.categoryNameTr ?? "kategori yok"} · ~${p.portionGram} g`,
                p.kcalPer100g != null
                    ? ` · ${p.kcalPer100g} kcal/100 g (USDA: ${p.usdaDescription ?? "elle"})`
                    : " · kalori bulunamadı")))));
}

async function removeItem(meal, foodId) {
    const items = itemsPayload(meal).filter((i) => i.foodId !== foodId);
    if (items.length === 0) {
        await api(`/api/meals/${meal.id}`, { method: "DELETE" });
    } else {
        await api(`/api/meals/${meal.id}`, {
            method: "PUT", body: JSON.stringify({ mealDate: meal.mealDate, mealType: meal.mealType, items }),
        });
    }
    await renderToday();
}

async function renderToday() {
    const list = $("#today");
    const meals = await api(`/api/meals?date=${today()}`);
    const rows = meals.flatMap((m) => m.items.map((i) => el("li", {},
        el("span", {},
            `${MEAL_LABELS[m.mealType]}: ${i.foodName} — ${i.portionGram} g`,
            i.kcal != null ? ` · ${Math.round(i.kcal)} kcal` : el("span", { class: "muted" }, " · kalori yok")),
        el("button", { class: "link", type: "button", title: "Sil", onclick: () => removeItem(m, i.foodId) }, "×"))));
    list.replaceChildren(...(rows.length ? rows : [el("li", { class: "muted" }, "Henüz bir şey eklenmedi.")]));
}

$("#food-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    const button = form.querySelector("button");
    const data = new FormData(form);
    button.disabled = true;
    $("#parsed").replaceChildren();
    setStatus("#food-status", "Anlaşılıyor… (Gemini kalemlere ayırıyor, kaloriler USDA'dan geliyor)");
    try {
        const res = await api("/api/meals/text", {
            method: "POST",
            body: JSON.stringify({ mealDate: today(), mealType: data.get("mealType"), text: data.get("text") }),
        });
        setStatus("#food-status", `${res.parsed.length} kalem eklendi. Porsiyonlar tahminidir.`);
        renderParsed(res.parsed);
        form.elements.namedItem("text").value = "";
        await renderToday();
    } catch (err) {
        setStatus("#food-status", err.message, true);
    } finally {
        button.disabled = false;
    }
});

// ---------- 3) Öneri ----------

async function loadConditions() {
    const codes = await api("/api/recommendation/conditions");
    $("#conditions").replaceChildren(...codes.map((code) => el("label", {},
        el("input", { type: "checkbox", value: code }), CONDITION_LABELS[code] ?? code)));
    $("#evening").checked = new Date().getHours() >= 17;
}

function sourceLink(src, text) {
    if (!src) return null;
    return el("a", { href: src.url, target: "_blank", rel: "noopener noreferrer" }, text ?? src.authority ?? src.title);
}

function noteList(notes, cls) {
    if (!notes?.length) return null;
    return el("ul", { class: cls }, notes.map((n) => el("li", {},
        `${n.noteTr} `, n.source ? el("span", { class: "muted" }, "(", sourceLink(n.source), ")") : null)));
}

function drinkCard(rec, rank, response) {
    const isBest = rank === 1;
    const byLlm = isBest && response.generatedBy === "rag_llm";
    return el("div", { class: isBest ? "drink best" : "drink" },
        el("h3", {}, `${rank}. ${rec.drink.nameTr}`,
            el("span", { class: "tag" }, `puan ${rec.score}`),
            isBest ? el("span", { class: "tag" }, "en uygun") : null),
        isBest
            ? el("p", {}, rec.reasonTr)
            : el("p", {}, rec.ruleReasons.join(" · ")),
        isBest ? null : el("p", { class: "muted" }, `Kanıt düzeyi: ${rec.drink.evidenceLevel}. ${rec.drink.evidenceNote ?? ""}`),
        byLlm && response.sources.length
            ? el("p", { class: "muted" }, "Kaynaklar: ",
                response.sources.flatMap((s, i) => [i ? " · " : "", sourceLink(s, s.title)]))
            : null,
        rec.cautions.length ? el("p", { class: "caution" }, "Dikkat:") : null,
        noteList(rec.cautions, "caution"),
        noteList(rec.infos, "muted"),
        el("p", { class: "muted" },
            byLlm ? "Gerekçe: Gemini, yalnızca kayıtlı kaynak parçalarından yazdı." : "Gerekçe: uygulamanın kuralları."));
}

function renderResult(r) {
    const parts = [];
    if (r.categories.length) {
        parts.push(el("p", { class: "muted" }, "Bugün: ",
            r.categories.map((c) => `${c.nameTr} (${c.foods.join(", ")})`).join("; ")));
    }
    if (r.uncategorizedFoods.length) {
        parts.push(el("p", { class: "muted" }, "Kategorisiz (hesaba katılmadı): ", r.uncategorizedFoods.join(", ")));
    }
    if (!r.recommendation) {
        parts.push(el("p", {}, r.message));
    } else {
        parts.push(drinkCard(r.recommendation, 1, r));
        r.alternatives.forEach((alt, i) => parts.push(drinkCard(alt, i + 2, r)));
    }
    if (r.excluded.length) {
        parts.push(el("h3", {}, "Durumların nedeniyle önerilmeyenler"),
            el("ul", {}, r.excluded.map((x) => el("li", {},
                `${x.drink.nameTr}: `,
                x.reasons.flatMap((n) => [`${n.noteTr} `,
                    n.source ? el("span", { class: "muted" }, "(", sourceLink(n.source), ") ") : null])))));
    }
    if (r.fallbackReason && r.generatedBy === "rules" && !r.fallbackReason.includes("llm=false")) {
        parts.push(el("p", { class: "muted" }, `Not: gerekçe kural tabanlı gösteriliyor (${r.fallbackReason})`));
    }
    // "sağlık tavsiyesi değildir" notu sayfanın altında sabit duruyor
    $("#result").replaceChildren(...parts);
}

$("#recommend-btn").addEventListener("click", async () => {
    const button = $("#recommend-btn");
    const conditions = [...document.querySelectorAll("#conditions input:checked")].map((c) => c.value);
    const params = new URLSearchParams({
        date: today(), conditions: conditions.join(","), evening: $("#evening").checked,
    });
    button.disabled = true;
    $("#result").replaceChildren();
    setStatus("#rec-status", "Öneri hazırlanıyor… (10 saniye kadar sürebilir)");
    try {
        renderResult(await api(`/api/recommendation?${params}`));
        setStatus("#rec-status", "");
    } catch (err) {
        setStatus("#rec-status", err.message, true);
    } finally {
        button.disabled = false;
    }
});

// ---------- Başlangıç ----------

$("#food-form select[name=mealType]").value = defaultMealType();
Promise.all([loadConditions(), renderToday()])
    .catch((err) => setStatus("#food-status", `Sunucuya ulaşılamadı: ${err.message}`, true));
