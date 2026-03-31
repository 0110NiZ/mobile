# SmartSchoolFinder Backend (MongoDB Reviews API)

## Requirements
- Node.js 18+ (recommended)
- A MongoDB Atlas database

## Setup
1. Install dependencies:
   - `npm install`

2. Create `.env` in this `backend/` folder (copy from `.env.example`):
   - Set `MONGODB_URI=...`
   - (Optional) `PORT=3000`

3. Start the server:
   - `npm start`

## Endpoints
- `GET /api/reviews/:schoolId`
  - Returns `{ averageRating, totalReviews, reviews }`

- `POST /api/reviews`
  - Body:
    ```json
    {
      "schoolId": "school_1",
      "reviewerName": "Guest User",
      "rating": 4,
      "comment": "Nice school"
    }
    ```

- `POST /api/reviews/seed`
  - Body:
    ```json
    {
      "schools": [
        { "id": "school_1", "name": "ABC School" }
      ]
    }
    ```
  - Only seeds if a school has **zero** reviews (prevents duplicates).

