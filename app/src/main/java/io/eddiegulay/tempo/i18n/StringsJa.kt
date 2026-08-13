package io.eddiegulay.tempo.i18n

/**
 * Japanese — the language Tempo was written in.
 *
 * Every value here is **transcribed, not authored**. The app shipped these exact strings as Kotlin
 * literals; this file moves them without editing them, so the migration is provably behaviour-neutral
 * for the language that already existed. If a string here reads oddly, that is how it reads on the
 * device today, and changing it is a separate decision from translating it.
 *
 * The counterpart is [StringsEn].
 */
object StringsJa : Strings {

    override val lang = Lang.Ja

    override val app = object : AppStrings {
        override val dockHome = "ホーム"
        override val dockSearch = "検索"
        override val dockNotifications = "通知"
        override val dockGym = "鍛錬"
        override val dockSetDefault = "Tempoを既定のホームに設定"
    }

    override val onboarding = object : OnboardingStrings {
        override val welcome = "ようこそ"
        override val preamble = "はじめる前に、Tempo が使う権限をお知らせします。" +
            "いずれも端末の中だけで使われ、外部へ送信されることはありません。"

        override val defaultHomeTitle = "既定のホーム"
        override val defaultHomeRationale = "ホームボタンを押したときに Tempo が開くようにします。" +
            "ランチャーとしての基本的な動作に必要です。"

        override val notificationAccessTitle = "通知へのアクセス"
        override val notificationAccessRationale = "受信した通知を読み取り、通知画面（通知）に静かに表示します。" +
            "内容が端末の外に出ることはありません。"

        override val grant = "許可"
        override val later = "後で"
        override val granted = "許可済み"
        override val laterSet = "後で設定"
        override val begin = "始める"

        override val languageLabel = "言語"
    }

    override val search = object : SearchStrings {
        override val heading = "けんさく"
        override val placeholder = "検索"
        override val empty = "見つかりません"
        override val loading = "・・・"

        override val hiddenApps = "非表示アプリ"
        override val toLightTheme = "ライトテーマに切り替え"
        override val toDarkTheme = "ダークテーマに切り替え"
        override val language = "言語"

        override val launch = "起動"
        override val menu = "メニュー"

        override val appInfo = "アプリ情報"
        override val hideApp = "非表示にする"
        override val uninstall = "アンインストール"

        override val updatedPrefix = "更新 "
    }

    override val dialog = object : DialogStrings {
        override val modeFocusTitle = "集中"
        override val modeFocusSubtitle = "時計だけの画面"
        override val modeGymTitle = "鍛錬"
        override val modeGymSubtitle = "体を動かす"
        override val dismiss = "やめる"

        override val languageTitle = "言語"
    }

    override val gym = object : GymStrings {
        override val tierBeginner = "入門"
        override val tierIntermediate = "中級"
        override val tierAdvanced = "上級"
    }

    override val fmt: Formats = JaFormats

    override val home: HomeStrings = JaHome
    override val filter: FilterStrings = JaFilter
    override val focus: FocusStrings = JaFocus
    override val calendar: CalendarStrings = JaCalendar
    override val notifications: NotificationsStrings = JaNotifications
    override val block: BlockStrings = JaBlock
    override val fault: FaultStrings = JaFault
    override val gymShared: GymSharedStrings = JaGymShared
    override val gymHome: GymHomeStrings = JaGymHome
    override val gymLibrary: GymLibraryStrings = JaGymLibrary
    override val gymExercise: GymExerciseStrings = JaGymExercise
    override val gymBuilder: GymBuilderStrings = JaGymBuilder
    override val gymSettings: GymSettingsStrings = JaGymSettings
    override val gymSession: GymSessionStrings = JaGymSession
    override val gymRecords: GymRecordsStrings = JaGymRecords
    override val gymCue: GymCueStrings = JaGymCue
    override val catalog: CatalogStrings = JaCatalog
}
