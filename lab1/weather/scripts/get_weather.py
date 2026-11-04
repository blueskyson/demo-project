#!/usr/bin/env python3
"""查詢指定城市的目前天氣與未來數日預報（使用 Open-Meteo，免 API key）。

用法:
    python3 get_weather.py <城市名稱> [--days N]

範例:
    python3 get_weather.py Taipei
    python3 get_weather.py Taichung --days 5
"""
import argparse
import json
import sys
import urllib.parse
import urllib.request

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

# WMO 天氣代碼 → 中文描述
WEATHER_CODES = {
    0: "晴朗", 1: "大致晴朗", 2: "局部多雲", 3: "陰天",
    45: "霧", 48: "霧淞",
    51: "毛毛雨（小）", 53: "毛毛雨（中）", 55: "毛毛雨（大）",
    56: "凍毛毛雨（小）", 57: "凍毛毛雨（大）",
    61: "小雨", 63: "中雨", 65: "大雨",
    66: "凍雨（小）", 67: "凍雨（大）",
    71: "小雪", 73: "中雪", 75: "大雪", 77: "雪粒",
    80: "陣雨（小）", 81: "陣雨（中）", 82: "陣雨（強）",
    85: "陣雪（小）", 86: "陣雪（大）",
    95: "雷雨", 96: "雷雨伴隨小冰雹", 99: "雷雨伴隨大冰雹",
}


def fetch_json(url, params):
    full_url = f"{url}?{urllib.parse.urlencode(params)}"
    with urllib.request.urlopen(full_url, timeout=15) as resp:
        return json.load(resp)


def geocode(city):
    data = fetch_json(GEOCODING_URL, {"name": city, "count": 5, "language": "zh", "format": "json"})
    results = data.get("results")
    if not results:
        return None
    # 同名地點可能有多個（例如 Springfield），取人口最多的那個
    return max(results, key=lambda r: r.get("population") or 0)


def describe(code):
    return WEATHER_CODES.get(code, f"未知天氣代碼 {code}")


def main():
    parser = argparse.ArgumentParser(description="查詢城市天氣")
    parser.add_argument("city", help="城市英文名稱，例如 Taipei、Taichung、Tokyo")
    parser.add_argument("--days", type=int, default=3, help="預報天數 1-16（預設 3）")
    args = parser.parse_args()

    days = max(1, min(args.days, 16))

    try:
        place = geocode(args.city)
        if place is None:
            print(f"錯誤：找不到城市「{args.city}」，請改用英文名稱或更完整的地名再試一次。")
            sys.exit(1)

        forecast = fetch_json(FORECAST_URL, {
            "latitude": place["latitude"],
            "longitude": place["longitude"],
            "current": "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m",
            "daily": "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
            "timezone": "auto",
            "forecast_days": days,
        })
    except Exception as e:  # 網路錯誤、逾時、API 異常
        print(f"錯誤：無法取得天氣資料（{e}）")
        sys.exit(2)

    location = "、".join(p for p in (place.get("name"), place.get("admin1"), place.get("country")) if p)
    cur = forecast["current"]
    print(f"地點：{location}（{place['latitude']:.2f}, {place['longitude']:.2f}）")
    print(f"觀測時間：{cur['time']}（{forecast['timezone']}）")
    print(f"目前天氣：{describe(cur['weather_code'])}")
    print(f"氣溫：{cur['temperature_2m']}°C（體感 {cur['apparent_temperature']}°C）")
    print(f"濕度：{cur['relative_humidity_2m']}%　風速：{cur['wind_speed_10m']} km/h")
    print()
    print(f"未來 {days} 天預報：")
    d = forecast["daily"]
    for i, date in enumerate(d["time"]):
        rain = d["precipitation_probability_max"][i]
        rain_text = f"{rain}%" if rain is not None else "N/A"
        print(f"  {date}  {describe(d['weather_code'][i])}  "
              f"{d['temperature_2m_min'][i]}~{d['temperature_2m_max'][i]}°C  降雨機率 {rain_text}")


if __name__ == "__main__":
    main()
