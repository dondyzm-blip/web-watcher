# WebWatcher - Android アプリ

Webページの変更を監視し、変更箇所をビジュアルに表示するAndroidアプリです。

## 機能

- 🔍 **URL監視** - 複数URLを指定間隔（15分〜24時間）で自動監視
- 🔔 **プッシュ通知** - 変更検知時に通知、タップで差分画面へ
- 📊 **差分ビュー** - 変更箇所を緑（追加）・赤（削除）でハイライト表示
- 📜 **アクセス履歴** - 最大50件のHTML/スナップショットを保存
- 🎯 **CSSセレクター** - 特定要素のみ監視可能（例: `#main-content`）
- ♻️ **再起動対応** - 端末再起動後も自動でWorker再スケジュール

## ビルド手順 (Android CLI)

### 前提条件
- Android SDK (API 34)
- JDK 17
- `androidcli` インストール済み

### 1. SDK パスを設定

```bash
cp local.properties.template local.properties
# local.properties の sdk.dir を実際のパスに変更
# 例: sdk.dir=/home/user/Android/Sdk
```

### 2. ビルド

```bash
cd WebWatcher
chmod +x gradlew

# デバッグビルド
./gradlew assembleDebug

# APKの場所
# app/build/outputs/apk/debug/app-debug.apk
```

### 3. インストール

```bash
# USBデバッグまたはWi-Fi ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

### androidcli を使う場合

```bash
# androidcli がインストール済みの場合
androidcli build --variant debug
androidcli install
```

## アプリの使い方

1. **URL追加** - FABボタン（＋）をタップ
2. **設定入力**
   - サイト名: 任意のタイトル
   - URL: 監視したいURL（https://...）
   - 監視間隔: 15分〜24時間
   - CSSセレクター（任意）: 特定箇所のみ監視
3. **保存** - 即時チェックが走り、以降は自動で定期監視

## アーキテクチャ

```
com.webwatcher
├── data/
│   ├── model/        # WatchTarget, AccessHistory
│   ├── db/           # Room Database, DAOs
│   └── repository/   # WatchRepository
├── service/
│   ├── WatchWorker.kt   # WorkManager バックグラウンド監視
│   ├── WatchScheduler   # スケジュール管理
│   └── BootReceiver.kt  # 再起動後の復元
├── ui/
│   ├── main/         # 一覧画面
│   ├── detail/       # 履歴・差分表示画面
│   └── add/          # URL追加・編集画面
└── util/
    ├── WebFetcher    # OkHttp HTTP取得
    ├── HashUtil      # SHA-256コンテンツハッシュ
    ├── HtmlDiffEngine  # LCSベース差分計算
    ├── SnapshotStorage # ファイル保存・管理
    └── NotificationHelper # 通知
```

## 差分アルゴリズム

- HTML取得後、Jsoupでscript/style等を除去してテキスト抽出
- SHA-256ハッシュで変更を高速検知
- 変更あり→LCSアルゴリズムで追加・削除テキストを特定
- 変更箇所をHTMLに埋め込み（緑=追加、赤=削除）→WebViewで表示

## ファイル構成

```
app/src/main/
├── AndroidManifest.xml
├── java/com/webwatcher/
│   ├── WebWatcherApp.kt
│   ├── data/...
│   ├── service/...
│   ├── ui/...
│   └── util/...
└── res/
    ├── layout/
    ├── drawable/
    ├── values/
    └── mipmap-*/
```
