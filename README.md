# 키워드 길찾기 (MapKeywords)

목적지 키워드를 미리 등록해 두고, 누르면 구글맵 길찾기를 바로 실행하는 안드로이드 앱입니다.

## 기능
- 키워드 등록 / 삭제 (길게 누르면 삭제)
- 키워드를 누르면 구글맵 길찾기 화면 실행 (출발지는 현재 위치)
- 이동수단(자동차 / 대중교통 / 도보 / 자전거) 선택값을 저장해 다음 실행 때도 유지
- 구글맵 API 키, 인터넷 권한, 위치 권한 모두 불필요 (구글맵 앱이 처리)

## APK 만드는 방법

### 방법 1. Android Studio (권장)
1. Android Studio 설치 (https://developer.android.com/studio)
2. `File > Open` 에서 이 `MapKeywords` 폴더 선택 후 Gradle Sync 완료까지 대기
3. 메뉴 `Build > Build Bundle(s) / APK(s) > Build APK(s)`
4. 완료 알림의 `locate`를 누르면 `app/build/outputs/apk/debug/app-debug.apk` 생성

### 방법 2. 명령줄
Android SDK와 JDK 17 이상이 설치된 PC에서 아래를 실행합니다.

```
gradle assembleDebug
```

(Gradle Wrapper를 쓰려면 먼저 `gradle wrapper --gradle-version 8.7` 실행)

## 폰에 설치
1. APK 파일을 폰으로 옮깁니다.
2. 파일을 열면 "출처를 알 수 없는 앱 설치"를 허용하라는 안내가 뜹니다. 허용 후 설치합니다.
3. 또는 폰의 USB 디버깅을 켜고 `adb install app-debug.apk`

## 구현 방식
구글맵 공식 URL 방식을 사용합니다.

```
https://www.google.com/maps/dir/?api=1&destination=<키워드>&travelmode=<driving|transit|walking|bicycling>
```
