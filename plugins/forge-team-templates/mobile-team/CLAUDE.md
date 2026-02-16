# Mobile Team

## Team Overview

We build and maintain the cross-platform mobile application using React Native. The app serves as the primary customer-facing interface for ordering, account management, real-time tracking, and notifications.

## Tech Stack

- **Framework**: React Native 0.75+ with New Architecture (Fabric, TurboModules)
- **Language**: TypeScript (strict mode)
- **State Management**: Zustand for global state, React Query for server state
- **Navigation**: React Navigation 7
- **Networking**: Axios with interceptors for auth, retry, and logging
- **Storage**: MMKV for key-value, WatermelonDB for offline-first data
- **Testing**: Jest + React Native Testing Library (unit), Detox (E2E)
- **CI/CD**: GitHub Actions with Fastlane for builds and store submission

## Build & Run

```bash
# Install dependencies
npm install

# Start Metro bundler
npx react-native start

# Run on iOS
npx react-native run-ios

# Run on Android
npx react-native run-android

# Run tests
npm test

# Run E2E tests (iOS)
npm run e2e:ios

# Run E2E tests (Android)
npm run e2e:android

# Lint
npm run lint

# Type check
npx tsc --noEmit
```

## Coding Conventions

### Component Structure
- Use functional components exclusively (no class components)
- Co-locate component, styles, types, and tests in the same directory
- Component file structure: `ComponentName/index.tsx`, `styles.ts`, `types.ts`, `__tests__/`
- Extract reusable UI components into `src/components/shared/`
- Screen components go in `src/screens/{FeatureName}/`

### State Management
- **Server state**: Always use React Query. Define query keys in `src/api/queryKeys.ts`
- **Global UI state**: Use Zustand stores in `src/stores/`. One store per domain
- **Local component state**: Use `useState` and `useReducer` for component-scoped state
- **Never** use global state for data that should be server state

### Navigation
- Define all routes as typed constants in `src/navigation/routes.ts`
- Use typed navigation hooks (`useTypedNavigation`, `useTypedRoute`)
- Deep linking configuration in `src/navigation/linking.ts`

### Networking
- All API calls go through the centralized Axios instance in `src/api/client.ts`
- Define API functions in `src/api/{domain}.ts` (e.g., `src/api/orders.ts`)
- Use React Query mutations for POST/PUT/DELETE operations
- Handle offline scenarios gracefully with queued mutations

### Naming
- Components: PascalCase (`OrderSummaryCard.tsx`)
- Hooks: camelCase with `use` prefix (`useOrderDetails.ts`)
- Utils: camelCase (`formatCurrency.ts`)
- Types: PascalCase with descriptive names (`OrderSummaryResponse`)
- Test files: `ComponentName.test.tsx`

### Testing
- Unit tests: Jest + RNTL for component rendering and interaction
- Hook tests: `renderHook` from RNTL
- E2E tests: Detox for critical user journeys (login, order placement, payment)
- Minimum 70% coverage for business logic hooks and utils
- Snapshot tests only for stable, shared UI components

### Performance
- Use `React.memo` for expensive list items
- Use `FlashList` instead of `FlatList` for long lists
- Lazy load screens with `React.lazy` and Suspense
- Monitor JS thread frame drops in development
- Image optimization: use WebP format, appropriate sizes, caching

## Security Rules

- NEVER store auth tokens in AsyncStorage (use Keychain/Keystore via react-native-keychain)
- NEVER log sensitive user data (PII, tokens, passwords)
- Certificate pinning enabled for all API calls in production builds
- Biometric authentication for sensitive operations
- Jailbreak/root detection with appropriate degraded functionality

## Active Forge Plugins

- forge-foundation
- forge-superagent
