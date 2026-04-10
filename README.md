# School Explorer

A One-Stop Hong Kong School Information Mobile Application for Android

## Project Overview

In Hong Kong, school information is often spread out on different websites and apps. You have to jump around a lot to find what you need, and many sites do not let you filter, compare schools, or see everything in one place.

School Explorer puts school data together in one Android app. You can search, filter, and compare schools more easily. The app supports English and Traditional Chinese so more people can use it, and we tried to keep the screens simple to use.

## Features

### 1. Search and Filtering

- Search schools by name.
- Filter by district, education level, gender, and distance.
- Sort the list using different options so the order updates right away.

### 2. Location and Distance Sorting

- Use your current GPS location, or set a location by hand.
- The app works out how far each school is from you.
- You can sort schools from nearest to farthest.
- If location is turned off or not allowed, distance is not shown.
- When we have the data, the app can show nearby transport (MTR, bus, minibus).

### 3. School Comparison

- Pick up to 2 schools to compare.
- See important details side by side, like district, school type, distance, and other key fields.

### 4. Favorites

- Save schools to your favorites list.
- Favorites are stored on the device and you open them from the sidebar.
- You can remove a school from favorites whenever you want.

### 5. School Information Display

- Shows school name, address, phone, and website.
- Also shows district, education level, and tuition fee where available.
- The website link opens in the browser when you tap it.

### 6. Map and Phone Actions

- Tap the map icon to open Google Maps for directions to the school.
- Tap the phone button to call the school directly.

### 7. User Reviews

- Write your own comments, edit them, or delete them.
- Like or dislike other people’s reviews.
- Reply under someone else’s comment.
- Sort reviews by latest, most positive, or most negative.

### 8. Notification System

- In the sidebar you get notifications when:
  - someone likes your comment,
  - someone dislikes your comment,
  - someone replies to your comment.

### 9. Voice Playback

- Text-to-speech reads out school names.
- Works in English and Chinese.
- Useful for pronunciation and for users who prefer listening.

### 10. UI/UX Features

- Dark mode and light mode.
- Bilingual interface (English + Traditional Chinese).
- Splash screen when the app starts.
- Short sound when you tap buttons (can feel more responsive).

## Technology Stack

- Android (Java)
- Android Studio
- XML layouts with Material Design
- RecyclerView for lists
- JSON and CSV for loading and handling data
- Android location services for distance

## Data Source

- [data.gov.hk](https://data.gov.hk) – Hong Kong government open data portal
- [EDB school location dataset (JSON)](https://www.edb.gov.hk/attachment/en/student-parents/sch-info/sch-search/sch-location-info/SCH_LOC_EDB.json) – main list we load in the app
- [EDB school search / profiles](https://www.edb.gov.hk/en/student-parents/sch-info/sch-search/index.html) – official school info pages
- Extra CSV and JSON files in the project (e.g. bundled assets)

## How to Run

1. Clone this repository to your computer.
2. Open the project folder in Android Studio.
3. Wait until Gradle finishes syncing (first time may take a few minutes).
4. Run the app on an emulator or plug in a real Android phone.

### Backend Setup (for review and comment features)

1. Open the terminal in the project (in Android Studio you can try **Alt + F12**).
2. Go into the backend folder: `cd backend`
3. Install packages (only needed the first time): `npm install`
4. Start the server: `npm run dev`

After that, run the Android app as usual.

## Notes

- You need an internet connection for parts of the app that load data online.
- Location permission is optional. Turn it on if you want distance sorting and distance on school cards.
