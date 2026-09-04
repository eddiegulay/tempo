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
        override val dockSearchAreas = "検索の範囲"
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

        override val handOffSection = "ほかで"
        override val handOffCall = "電話する"
        override val handOffWhatsApp = "WhatsApp"
        override val handOffWhatsAppNumberSubtitle = "この番号へ"
        override val handOffFindContacts = "連絡先で探す"
        override val handOffSearchContacts = "連絡先を検索"
        override val handOffSearchWhatsApp = "WhatsAppで探す"
        override val handOffSearchGoogle = "Googleで探す"
        override val handOffComposeEmail = "メールを書く"
        override val handOffSearchMail = "メールを検索"
        override val handOffCalendarSection = "予定"
        override val peopleSection = "ひと"
        override val contactMessage = "メッセージ"
        override val contactsAllow = "連絡先を許可"
        override val contactsAllowHint = "名前で探すには連絡先の許可が要ります"

        override val categoryGame = "ゲーム"
        override val categoryAudio = "音楽"
        override val categoryVideo = "動画"
        override val categoryImage = "画像"
        override val categorySocial = "社交"
        override val categoryNews = "報道"
        override val categoryMaps = "地図"
        override val categoryProductivity = "仕事"
        override val categoryAccessibility = "補助"
    }

    override val searchAreas = object : SearchAreasStrings {
        override val kana = "はんい"
        override val title = "検索の範囲"
        override val subtitle = "検索が見にいく先。連絡先は端末から読みます。メールとウェブはほかのアプリへ渡します。予定はTempoがすでに読んでいる日程です。"
        override val toggleOn = "入"
        override val toggleOff = "切"
        override val calendarNeedsAccess = "カレンダーへのアクセスが必要です"
        override val contactsNeedsAccess = "連絡先の閲覧がまだ許可されていません"
        override val apps = "アプリ"
        override val phone = "電話"
        override val contacts = "連絡先"
        override val whatsApp = "WhatsApp"
        override val google = "Google"
        override val email = "メール"
        override val calendar = "予定"
    }

    override val dialog = object : DialogStrings {
        override val modeFocusTitle = "集中"
        override val modeFocusSubtitle = "時計だけの画面"
        override val modeGymTitle = "鍛錬"
        override val modeGymSubtitle = "体を動かす"
        override val dismiss = "やめる"


        override val eventCreate = object : WriteConfirmStrings {
            override val heading = "予定を加えますか"
            override val confirm = "加える"
            override val consequence = "ほかの端末のカレンダーにも表示されます。"
        }

        override val eventUpdate = object : WriteConfirmStrings {
            override val heading = "予定を変えますか"
            override val confirm = "変える"
            override val consequence = "変更はほかの端末のカレンダーにも反映されます。"
        }

        override val eventDelete = object : WriteConfirmStrings {
            override val heading = "予定を削除しますか"
            override val confirm = "削除する"
            override val consequence = "ほかの端末のカレンダーからも消えます。元に戻せません。"
        }

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
