# Username/Password Login (merged login) — design

Date: 2026-08-03
Branch: feat/android-tv

## Goal

Let users sign in to the Android apps with a NeuroKaraoke **username + password**,
in addition to the existing Discord flow. Both yield the same NeuroKaraoke JWT
(`apiToken`) that already drives favourites/playlists/profile sync. Requested by
users (Discord: "is the merged login still in plans for the apps?"); the iOS app
already uses this endpoint.

## API contract (discovered)

- `POST https://idk.neurokaraoke.com/api/auth/login`
  - Body: `{"username": "<name>", "password": "<pass>"}` (email is rejected — username only; password ≥ 6 chars).
  - 200 → JSON containing the JWT under `token` (fall back to `accessToken`, matching the existing Discord token-exchange parse in `AuthRepository`).
  - 401 → `{"message":"Invalid username or password."}`.
  - 400 → `{"message":"..."}` validation error (e.g. "Password must be at least 6 characters").
- `POST /api/auth/register` also exists (out of scope — register on the web).

## Token → User

The password-login JWT does **not** carry Discord claims, so `parseJwtAndSaveUser`
(which requires Discord claim URIs) can't populate the User. Instead construct a
minimal `User(id = username, username = username, discriminator = "0", avatar = null,
apiToken = jwt)` and `saveUser` it. The avatar/level/badges are enriched afterward by
`ProfileViewModel.load(token)` (already called by the Account screens), which fetches
the real profile with the JWT. `getAccessToken()` already returns `apiToken` first, so
all authed API calls work unchanged.

## Components

### Data — `AuthRepository.kt`
- `suspend fun loginWithPassword(username: String, password: String): Result<User>` —
  POST the login body via `HttpURLConnection` (mirrors `handleAuthCallback` style),
  `Dispatchers.IO`. On `HTTP_OK`: extract JWT via `parseLoginToken(body)`, build the
  minimal User, `saveUser`, return success. On 401/400: `Result.failure(Exception(<message from body, or a default>))`. Network error → failure.
- `fun parseLoginToken(json: String): String` — `JSONObject(json).optString("token", optString("accessToken",""))`. Pure/JVM-testable.
- Add const `LOGIN_URL = "https://idk.neurokaraoke.com/api/auth/login"`.

### ViewModel — `AuthViewModel.kt`
- `fun loginWithPassword(username: String, password: String)` — sets `isLoading=true`,
  calls the repo, on success updates `user/isLoggedIn`, on failure sets `error`.
  (uiState already has `isLoading`/`error`.)

### UI — TV (`ui/tv/`)
- New `TvLoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit)`: two fields (username,
  password) driven by the existing `TvKeyboard` (D-pad text entry; password field masks
  display), a Sign-in button, and an inline error line from `AuthUiState.error`. On
  success (`isLoggedIn` becomes true) call `onLoggedIn`.
- `TvAccountScreen`: the signed-out prompt gains a second choice — "Sign in with
  username" (→ `TvLoginScreen`) alongside the existing "Pair a device" (→ `TvPairScreen`).

### UI — Phone (`ui/`)
- A `UsernameLoginDialog` (or small screen) with username + password `OutlinedTextField`s
  (password `visualTransformation = PasswordVisualTransformation()`), a Sign-in button,
  progress + error. Reachable from the same entry that today launches Discord
  (`MainScreen` sign-in): present a small chooser — "Discord" or "Username & password".
- Calls `authViewModel.loginWithPassword(...)`.

## Testing

- JVM unit test (`AuthRepositoryLoginTest` or reuse existing test file): `parseLoginToken`
  extracts `token`, falls back to `accessToken`, returns "" when absent.
- Manual/emulator: TV login form signs in with a real account → Account screen shows the
  profile; favourites/Your-Playlists populate (proves the JWT works). Phone likewise.

## Security notes

- The app never logs the password; it is sent once over HTTPS and not stored. Only the
  JWT is persisted (same `KEY_API_TOKEN` SharedPreferences slot as today).
- Claude builds the form; the **user** types their own credentials.

## Non-goals (v1)

Registration and password reset (web only); "account merging" logic beyond both methods
yielding a JWT; biometric/remember-me; Car surface.
