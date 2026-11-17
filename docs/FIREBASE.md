# Firebase: fan community

The Community tab in Favourites uses Firebase Authentication (anonymous only) and Cloud
Firestore. Nothing else from Firebase is included.

## How it works

- Members pick a username; they sign in anonymously behind the scenes (no email, phone or password).
- One feed for all fans (`communities/all`) and one per followed team (`communities/team-<id>`).
  Teams that can be followed are capped at 5.
- Opinions (agree / disagree) and polls (2 to 4 options). One vote per post per member.
- One post per member per 24 hours, enforced by the rules through `users/{uid}.lastPostAt`.
- Every post, vote and report has `expireAt` = 24 hours after the post. TTL deletes them; the app
  hides and deletes its copies at the same time.
- Posts already seen are kept in Room; opening a feed downloads only newer posts. The refresh
  button downloads the whole last 24 hours (updated vote counts, deleted posts).
- Report hides a post on the phone and counts a report; 5 reports hide it for everyone. Members accept the community rules (`Communities.RULES`) when joining. Block
  hides an author on the phone. A small word list rejects abusive usernames and posts.

## Console setup (project `criczy-17fe7`)

1. **Authentication** > Get started > Sign-in method > **Anonymous** > Enable > Save.
2. **Firestore Database** > Create database > Standard edition > location near users
   (e.g. `asia-south1`, cannot be changed later) > **Production mode** > Create.
3. **Firestore** > Rules: replace everything with `firebase/firestore.rules` from this repository > Publish.
4. **Firestore** > TTL (in Google Cloud console: Firestore > Time-to-live) > create three policies, all on
   field `expireAt`: collection group `posts`, `votes`, `reports`. Deletion happens within about a
   day of expiry; the app already hides expired posts.
5. Optional, for debug builds (`com.criczy.app.debug`): Project settings > Add app > Android with
   that package name, then download `google-services.json` again into `app/`. Until then debug
   builds show "Community unavailable"; release builds work.

`app/google-services.json` is git-ignored. Without it (CI, fresh clones) the Google Services plugin is
not applied and the app builds with the community unavailable.

Free (Spark) plan limits per day: 50,000 reads, 20,000 writes, 20,000 deletes.
