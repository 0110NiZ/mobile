# School Explorer

**COMP3130SEF | Group Project**

A One-Stop Hong Kong School Information Mobile Application for Android

## Project Overview

In Hong Kong, school information is often spread out on different websites and apps. You have to jump around a lot to find what you need, and many sites do not let you filter, compare schools, or see everything in one place.

School Explorer puts school data together in one Android app. You can search, filter, and compare schools more easily. The app supports English and Traditional Chinese so more people can use it, and we tried to keep the screens simple to use.

## Features

### 1. Search and Filtering

- Search by name and filter by district, education level, gender, and distance.
- Sort the list with different options so the order updates straight away.

### 2. Location and Distance Sorting

- Use GPS or pick a location manually as your reference point.
- Distances are calculated and you can sort from nearest to farthest; if location is off or denied, distance is hidden.
- Nearby transport (MTR, bus, minibus) shows up when we have that data.

### 3. School Comparison

- Pick up to 2 schools and view them together.
- Main details (district, type, distance, etc.) are shown side by side.

### 4. Favorites

- Save schools locally, open the list from the sidebar, and remove items anytime.

### 5. School Information Display

- Shows name, address, phone, website, district, education level, and tuition fee when available.
- Tap the website to open it in the browser.

### 6. Map and Phone Actions

- Tap the map icon to open Google Maps for directions.
- Tap the phone button to call the school.

### 7. User Reviews

- Post, edit, or delete your own comments.
- Like, dislike, or reply to other people’s reviews.
- Sort by latest, most positive, or most negative.

### 8. Notification System

- New alerts show up in the sidebar.
- You get notified when someone likes, dislikes, or replies to your comment.

### 9. Voice Playback

- Text-to-speech reads school names in English and Chinese.
- Handy for pronunciation and if you prefer audio over reading.

### 10. UI/UX Features

- Dark mode, light mode, and a bilingual UI (English + Traditional Chinese).
- Splash screen on startup and short sounds on button taps.

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
