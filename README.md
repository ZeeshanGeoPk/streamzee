# Streamzee 🎬 `v1.0.0-beta1`

**Streamzee** is a clean, cinematic, and unified streaming hub for Android. It brings together Movies, TV Series, and Anime into a single, high-performance interface built entirely with Jetpack Compose.

> **⚠️ Beta Phase Notice:** This application is currently in its early beta. While core streaming functionality is stable, the **Downloads** and **Profile** tabs are currently under development and will be available in future updates.

---

## 📱 App UI Gallery

### 1. Setup & Discovery
| TMDB Key Setup | Home Dashboard | Explore & Search |
|:---:|:---:|:---:|
| ![Setup](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-02-36.png) | ![Home](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-20-03.png) | ![Search](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-20-37.png) |

### 2. Movies & TV Series
| Cinematic Details | Smart Resume Logic |
|:---:|:---:|
| ![Details](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-22-02.png) | ![Resume](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-23-30.png) |

### 3. Anime Experience (Jikan + MegaPlay)
| Anime Search | Details & Translation | Episode Selection |
|:---:|:---:|:---:|
| ![AnimeSearch](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-30-29.png) | ![AnimeDetails](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-31-58.png) | ![AnimeEpisodes](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-32-19.png) |

### 4. Personal Library
| Unified Watchlist | Filtered View |
|:---:|:---:|
| ![Watchlist](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-33-05.png) | ![WatchlistEmpty](Screenshots/appSS/Screenshot%20from%202026-05-27%2016-34-02.png) |

---

## ✨ Features

- **Triple Content Hub:** Stream Movies, TV Shows, and Anime.
- **Explore & Discover:** Explore trending, top-rated, and personalized recommendations.
- **Search:** Find any title across all categories with a powerful search engine.
- **No Ads & No Tracking:** Built with privacy in mind. No interruptions, no analytics, and no data collection.
- **Unified Watchlist:** Save any title across all categories into one organized library.
- **Modern UI:** AMOLED-ready dark theme with smooth animations and cinematic transitions.

---

## ⚙️ Initial Setup (Technical Step)

To maintain a high-quality experience without centralized servers, Streamzee requires a **TMDB API Read Access Token**. This is a one-time technical setup. I am planning to implement a user-friendly setup without needing the TMDB token in future updates, but for now, this is required to fetch metadata and streaming links.

### How to get your token:
1.  Visit [The Movie Database (TMDB)](https://www.themoviedb.org/).
2.  Create a free account and verify your email.
3.  Go to **Account Settings > API**.
4.  Generate a "Developer" API Key.
5.  Copy the **"API Read Access Token"** (the very long string).
6.  Launch Streamzee and paste the token when prompted on the Setup Screen.

---

## 🛠 Project Roadmap

| Feature | Status |
|:--- |:--- |
| **Movie/TV Streaming** | ✅ Stable |
| **Anime Streaming** | ✅ Stable |
| **Search Functionality** | ✅ Stable |
| **Watchlist Logic** | ⚠️ UnStable |
| **Bug Fixes (UI/Scaling)** | 🛠 Ongoing |
| **Downloads Section** | 🚧 Coming Soon |
| **User Profile/Stats** | 🚧 Coming Soon |

---

## ❤️ Support & Sponsorship

Streamzee is a solo-developer project. It is free to use and open-source. If you find the app useful and want to help me speed up the development of the "Downloads" feature and fix existing bugs, please consider sponsoring:

- **[Support me on BuyMeACoffee](https://www.buymeacoffee.com/yourusername)**
- **[Sponsor via GitHub](https://github.com/sponsors/yourusername)**

Your contributions help cover development tools and keep the project ad-free!

---

## 🛡 Disclaimer

Streamzee is a browser-based metadata aggregator. It does not host, store, or distribute any media files. All content is linked via third-party streaming embeds and publicly available metadata APIs. Users are responsible for ensuring their usage complies with local laws.

---
**Version:** 1.0.0 Beta  
**Framework:** Jetpack Compose (Kotlin)  
**License:** MIT