const cachedMTRStops = [];
const cachedBusStops = [];
const cachedMinibusStops = [];

function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
  const R = 6371000; // meters
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

function initTransportData() {
  cachedMTRStops.length = 0;
  cachedBusStops.length = 0;
  cachedMinibusStops.length = 0;

  // MTR (mock but real HK station coordinates); name_zh = Traditional Chinese for app locale.
  cachedMTRStops.push(
    { name: "Tai Po Market Station", name_zh: "大埔墟站", latitude: 22.4445, longitude: 114.1703 },
    { name: "Tai Wo Station", name_zh: "太和站", latitude: 22.4509, longitude: 114.1616 },
    { name: "Kowloon Tong Station", name_zh: "九龍塘站", latitude: 22.3372, longitude: 114.1767 },
    { name: "Sha Tin Station", name_zh: "沙田站", latitude: 22.3834, longitude: 114.1887 },
    { name: "University Station", name_zh: "大學站", latitude: 22.4136, longitude: 114.2106 }
  );

  // Bus stops (mock but real HK-style stop locations)
  cachedBusStops.push(
    { name: "Tai Yuen Estate Stop (KMB 71K)", name_zh: "大元邨站（九巴71K）", latitude: 22.4501, longitude: 114.1701 },
    { name: "Kwong Fuk Road Stop (KMB 72A)", name_zh: "廣福道站（九巴72A）", latitude: 22.4484, longitude: 114.1685 },
    { name: "Kowloon Tong Station Bus Terminus", name_zh: "九龍塘鐵路站巴士總站", latitude: 22.3366, longitude: 114.1761 },
    { name: "La Salle Road Stop", name_zh: "喇沙利道站", latitude: 22.3342, longitude: 114.1793 },
    { name: "Sha Tin Central Bus Terminus", name_zh: "沙田市中心巴士總站", latitude: 22.3814, longitude: 114.1882 }
  );

  // Minibus stops (mock but real HK-style stop locations)
  cachedMinibusStops.push(
    { name: "Green Minibus 20K Stop", name_zh: "專線小巴20K站", latitude: 22.4461, longitude: 114.1698 },
    { name: "Green Minibus 20A Stop", name_zh: "專線小巴20A站", latitude: 22.4505, longitude: 114.1677 },
    { name: "Green Minibus 28K Stop", name_zh: "專線小巴28K站", latitude: 22.4490, longitude: 114.1661 },
    { name: "Green Minibus 25M (Kowloon Tong) Stop", name_zh: "專線小巴25M站（九龍塘）", latitude: 22.3369, longitude: 114.1780 },
    { name: "Green Minibus 65A (Sha Tin) Stop", name_zh: "專線小巴65A站（沙田）", latitude: 22.3812, longitude: 114.1875 }
  );
}

module.exports = {
  cachedMTRStops,
  cachedBusStops,
  cachedMinibusStops,
  initTransportData,
  calculateHaversineDistance
};

