function shuffle(array) {
  for (let i = array.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [array[i], array[j]] = [array[j], array[i]];
  }
  return array;
}

function buildSeedPlan() {
  // 20 reviews: 5★x4, 4★x5, 3★x4, 2★x4, 1★x3
  return shuffle([5, 5, 5, 5, 4, 4, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1]);
}

function pickNonRepeating(pool, usedSet) {
  const candidates = pool.filter((t) => !usedSet.has(t));
  const pickFrom = candidates.length > 0 ? candidates : pool;
  const chosen = pickFrom[Math.floor(Math.random() * pickFrom.length)];
  usedSet.add(chosen);
  return chosen;
}

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function buildReactions(rating) {
  // Make it feel natural: higher rating tends to get more likes, fewer dislikes.
  if (rating >= 4) {
    return { likes: randInt(6, 30), dislikes: randInt(0, 6) };
  }
  if (rating === 3) {
    return { likes: randInt(2, 20), dislikes: randInt(0, 10) };
  }
  // rating 1-2
  return { likes: randInt(0, 18), dislikes: randInt(2, 15) };
}

function buildSeedReviewsForSchool({ schoolId, schoolName, count = 20, existingComments = [] }) {
  const names = shuffle([
    "Amy",
    "Chris",
    "Jason",
    "Kelly",
    "Leo",
    "Mandy",
    "Ryan",
    "Sophie",
    "Ethan",
    "Nina",
    "Olivia",
    "Ben"
  ]);

  const enPositives = [
    "The teachers are very supportive and the campus is clean.",
    "Good learning atmosphere and friendly staff.",
    "Great activities and the school feels safe.",
    "Convenient location and the facilities are well maintained.",
    "Teachers communicate clearly and students are encouraged."
  ];
  const enNeutrals = [
    "Facilities are okay but could be improved.",
    "Overall decent, but some classes feel too crowded.",
    "Transport is fine, but the schedule can be a bit tight.",
    "The environment is acceptable, not outstanding.",
    "Some teachers are excellent, others are average."
  ];
  const enNegatives = [
    "Too much homework and communication is not very good.",
    "The workload is heavy and feedback can be slow.",
    "Facilities feel outdated and need upgrades.",
    "Management could be more responsive to parents.",
    "The campus gets noisy during peak hours."
  ];

  const zhPositives = [
    "老師很有耐心，課堂氣氛很不錯，校園也很整潔。",
    "學校活動多，學生有很多發揮的機會。",
    "交通方便，接送位置清晰，家長溝通也到位。",
    "學習氛圍濃厚，同學之間互相幫忙。",
    "校風較好，老師願意聽取意見並給建議。"
  ];
  const zhNeutrals = [
    "整體還可以，但部分設施有點舊，希望可以改善。",
    "功課量適中，不算太輕鬆也不至於壓力過大。",
    "教學安排算穩定，但有些課程節奏偏快。",
    "環境尚可，校園空間略擁擠但能接受。",
    "家校溝通普通，有時候訊息更新不夠快。"
  ];
  const zhNegatives = [
    "功課偏多，孩子壓力比較大，休息時間不夠。",
    "部分老師要求高但指導不夠清楚，需要家長多跟進。",
    "設施較舊，維修速度一般，影響使用體驗。",
    "校內管理有時不夠彈性，處理問題較慢。",
    "交通安排不夠理想，上下學時段比較混亂。"
  ];

  const used = new Set(existingComments.map((c) => String(c || "").trim()).filter(Boolean));
  const usedTemplates = new Set();

  const plan = buildSeedPlan(); // 20 ratings with required distribution
  const ratings = plan.slice(0, count);

  const languages = shuffle(
    Array.from({ length: count }, (_, i) => (i < Math.floor(count / 2) ? "en" : "zh"))
  );

  return ratings.map((rating, idx) => {
    const lang = languages[idx] || "en";
    let templatePool;
    if (lang === "zh") {
      templatePool = zhNeutrals;
      if (rating >= 4) templatePool = zhPositives;
      if (rating <= 2) templatePool = zhNegatives;
    } else {
      templatePool = enNeutrals;
      if (rating >= 4) templatePool = enPositives;
      if (rating <= 2) templatePool = enNegatives;
    }

    const base = pickNonRepeating(templatePool, usedTemplates);
    const schoolBit = schoolName ? ` (${schoolName})` : "";
    let comment = idx % 4 === 0 ? `${base}${schoolBit}` : base;
    if (lang === "zh" && idx % 5 === 0 && schoolName) {
      comment = `${comment}（${schoolName}）`;
    }
    // Ensure comment uniqueness for this school across top-up seed runs.
    if (used.has(comment)) {
      comment = `${comment} #${randInt(2, 99)}`;
    }
    used.add(comment);

    const reactions = buildReactions(rating);

    return {
      schoolId,
      reviewerName: names[idx % names.length],
      rating,
      comment,
      isSeeded: true,
      likes: reactions.likes,
      dislikes: reactions.dislikes
    };
  });
}

module.exports = { buildSeedReviewsForSchool };

