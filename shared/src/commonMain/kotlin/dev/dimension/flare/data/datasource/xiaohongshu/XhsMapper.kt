package dev.dimension.flare.data.datasource.xiaohongshu

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.xiaohongshu.model.XhsComment
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFeedItem
import dev.dimension.flare.data.network.xiaohongshu.model.XhsImage
import dev.dimension.flare.data.network.xiaohongshu.model.XhsInteractInfo
import dev.dimension.flare.data.network.xiaohongshu.model.XhsNoteCard
import dev.dimension.flare.data.network.xiaohongshu.model.XhsNoteContext
import dev.dimension.flare.data.network.xiaohongshu.model.XhsSearchUser
import dev.dimension.flare.data.network.xiaohongshu.model.XhsUser
import dev.dimension.flare.data.network.xiaohongshu.model.XhsUserInfoData
import dev.dimension.flare.data.network.xiaohongshu.model.XhsUserMe
import dev.dimension.flare.data.network.xiaohongshu.model.XhsVideo
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlin.time.Instant

private const val XHS_MEDIA_REFERER = "https://www.xiaohongshu.com/"
private const val XHS_MEDIA_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/145.0.0.0 Safari/537.36"

private val xhsMediaHeaders =
    persistentMapOf(
        "Referer" to XHS_MEDIA_REFERER,
        "User-Agent" to XHS_MEDIA_USER_AGENT,
    )
private val xhsInlineTokenRegex = Regex("#([^#\\[\\]\\n]+)\\[话题]#|\\[[^\\[\\]\\s]{1,16}\\]")

// Extracted from the official xhs-pc-web runtime module 48828 in index.7b16c13c.js.
private val xhsStaticEmojiMap =
    mapOf(
        "[微笑R]" to "https://picasso-static.xiaohongshu.com/fe-platform/6ae82231880cc1f1961709f0870d75c9fda9117e.png",
        "[害羞R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b3e24005247a1af7081e545f32d81ce4d46ceca4.png",
        "[失望R]" to "https://picasso-static.xiaohongshu.com/fe-platform/274d668da630d35a120e8e2f6f4c6f92e564c310.png",
        "[汗颜R]" to "https://picasso-static.xiaohongshu.com/fe-platform/a3792d9dfe45d2d5fe508fd6a76c071094478f07.png",
        "[哇R]" to "https://picasso-static.xiaohongshu.com/fe-platform/4bf9381c930ba0769a15f6acb3ccc2b4339a0fe3.png",
        "[喝奶茶R]" to "https://picasso-static.xiaohongshu.com/fe-platform/183e8a8fa3f3bae442e4a85c42bd0ab46156bb57.png",
        "[自拍R]" to "https://picasso-static.xiaohongshu.com/fe-platform/c53a77c2b4c87094120e4b31074475bf159d2cf0.png",
        "[偷笑R]" to "https://picasso-static.xiaohongshu.com/fe-platform/0e549ce3e730b934cd54dc64626c430594cd23ec.png",
        "[飞吻R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b0621834bba2ec0b8261d192403e888aa95e4521.png",
        "[石化R]" to "https://picasso-static.xiaohongshu.com/fe-platform/6dd6a2465801fc620967ed968cf051eadaf3644d.png",
        "[笑哭R]" to "https://picasso-static.xiaohongshu.com/fe-platform/eba2fc6cf81cc80f26a9c94c8fea0b0b50e92427.png",
        "[赞R]" to "https://picasso-static.xiaohongshu.com/fe-platform/3d2764c92f61959bece9598fc02742653ff7d286.png",
        "[暗中观察R]" to "https://picasso-static.xiaohongshu.com/fe-platform/8c0039f06846f50a569a3f47785d672c92fea808.png",
        "[买爆R]" to "https://picasso-static.xiaohongshu.com/fe-platform/8e050cfccdcaed5aa5b782cc7fe5b2a6d22b811a.png",
        "[大笑R]" to "https://picasso-static.xiaohongshu.com/fe-platform/c80b00b248813330c081eeb2f11a38506e88100b.png",
        "[色色R]" to "https://picasso-static.xiaohongshu.com/fe-platform/eecb2c239e376fe66f282a3ae3eefa12efb5961e.png",
        "[生气R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d756269a0b362c89a74cb189489a97744c9232f5.png",
        "[哭惹R]" to "https://picasso-static.xiaohongshu.com/fe-platform/e69746a30a0362ccd1eccde8571866f0225c58f2.png",
        "[萌萌哒R]" to "https://picasso-static.xiaohongshu.com/fe-platform/42f30cb59e9b8230ba0b6202e2dab23717a91af0.png",
        "[斜眼R]" to "https://picasso-static.xiaohongshu.com/fe-platform/4d0295283b67bf0513fcfef07ec4a57d0ebfda91.png",
        "[可怜R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ebc690ef847746c3259e9d7492a1563920a4d26f.png",
        "[鄙视R]" to "https://picasso-static.xiaohongshu.com/fe-platform/99dc85d9a79d09352a6e876e0af65241fb05efab.png",
        "[皱眉R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b38cd520632fe0ce51379fb2df743baa67f09f3d.png",
        "[抓狂R]" to "https://picasso-static.xiaohongshu.com/fe-platform/18670d01d153c42a3e547ca89f8ac40e7bf4ae8e.png",
        "[捂脸R]" to "https://picasso-static.xiaohongshu.com/fe-platform/9fdeb5b943fc0ac1d867b65b478dbca6732b2394.png",
        "[派对R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ed1ef2cb6cd8e3a48ff3b752b01317bb4e0d0cac.png",
        "[吧唧R]" to "https://picasso-static.xiaohongshu.com/fe-platform/f3daf7ec77dd0722065e451cf015c3c2f97f7904.png",
        "[惊恐R]" to "https://picasso-static.xiaohongshu.com/fe-platform/da18c61d523830bb9822a4c8b6cd8e2e1f1fbf7d.png",
        "[抠鼻R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b55e0508dcfc5449269d0b524578fdc10999810c.png",
        "[再见R]" to "https://picasso-static.xiaohongshu.com/fe-platform/53d7029df310ccdbb67587018fbc3747fab823fc.png",
        "[叹气R]" to "https://picasso-static.xiaohongshu.com/fe-platform/a5a07ebdeb0ac0f208f0ef428f66ba5f1f7eaef4.png",
        "[睡觉R]" to "https://picasso-static.xiaohongshu.com/fe-platform/dfb2c7f84e6042435f2cc6663e6b63cf0ca246b4.png",
        "[得意R]" to "https://picasso-static.xiaohongshu.com/fe-platform/f30923a7b86fe8ca74819acf152a51c6e5e02fdb.png",
        "[吃瓜R]" to "https://picasso-static.xiaohongshu.com/fe-platform/0330af4d8b15e80c629eb9cbb158cc7522c82583.png",
        "[扶墙R]" to "https://picasso-static.xiaohongshu.com/fe-platform/0e12d96d712f3839c218f2dedf7b6821e78a8e3a.png",
        "[黑薯问号R]" to "https://picasso-static.xiaohongshu.com/fe-platform/a72932f04f2e89d3839547762a2037d218c62b31.png",
        "[黄金薯R]" to "https://picasso-static.xiaohongshu.com/fe-platform/79efc4110f6dec2deac59573f94a64b7e9cfdae4.png",
        "[吐舌头H]" to "https://picasso-static.xiaohongshu.com/fe-platform/5aa0b2c07643a215a88f2f805ae2ced7dd150ee6.png",
        "[扯脸H]" to "https://picasso-static.xiaohongshu.com/fe-platform/229221a73144192a7b7c3e949853f550a5ef24a4.png",
        "[doge]" to "https://picasso-static.xiaohongshu.com/fe-platform/16a941439d3f2fc3c23ece4128e7c90c38cf6a87.png",
        "[惊吓H]" to "https://picasso-static.xiaohongshu.com/fe-platform/da18c61d523830bb9822a4c8b6cd8e2e1f1fbf7d.png",
    )

// Extracted from the official web redmoji API: GET /api/im/redmoji/detail, version 5.
private val xhsDynamicEmojiMap =
    mapOf(
        "[蹲后续H]" to "https://picasso-static.xiaohongshu.com/fe-platform/a633dcf8d48c500ae11532d0583c529b89286c66.webp",
        "[天幕R]" to "https://fe-video-qc.xhscdn.com/fe-platform/7642341e830f97c45f3261b9adee8b5a7336499d.png",
        "[卡式炉R]" to "https://fe-video-qc.xhscdn.com/fe-platform/783dc5c9039dab7920f60b69a0fe57e77302ddcd.png",
        "[折叠椅R]" to "https://fe-video-qc.xhscdn.com/fe-platform/359bd197c452258888f4f3f224d40d140b1247c3.png",
        "[营地车R]" to "https://fe-video-qc.xhscdn.com/fe-platform/8d3e5b8a06eda42229adf550d930bb8e4aaae9b7.png",
        "[露营灯R]" to "https://fe-video-qc.xhscdn.com/fe-platform/093dd4338b46ca52074d060c1c75ce04697af6d4.png",
        "[露营R]" to "https://picasso-static.xiaohongshu.com/fe-platform/bc046729a7265fa579fb9c26289f9e9fcaa83beb.png",
        "[渔夫帽R]" to "https://fe-video-qc.xhscdn.com/fe-platform/8a513afd61b9dd09c9e138e882e92ff9cae14649.png",
        "[登山鞋R]" to "https://fe-video-qc.xhscdn.com/fe-platform/fe65c446020944558c142d288e095e5484cba90f.png",
        "[背包R]" to "https://fe-video-qc.xhscdn.com/fe-platform/d9f0f58518cc9a1d73caf97ff5b0ecb4fd5a741b.png",
        "[马甲R]" to "https://fe-video-qc.xhscdn.com/fe-platform/2fda1b2115dccf04ac5143210b8d83f352f73e2c.png",
        "[骑行服R]" to "https://fe-video-qc.xhscdn.com/fe-platform/ffc5912b221563c0a7f3fd751b87e27f7dd5318a.png",
        "[手套R]" to "https://fe-video-qc.xhscdn.com/fe-platform/9572fe03b56aef9ec8a1e79dac64d4225a2e380a.png",
        "[头盔R]" to "https://fe-video-qc.xhscdn.com/fe-platform/2a5fe9cfad77cfcf632c1cb6123e68250afcbff2.png",
        "[风镜R]" to "https://fe-video-qc.xhscdn.com/fe-platform/0e7fb713c7fca2e381a40f590a46a262780df631.png",
        "[公路车R]" to "https://fe-video-qc.xhscdn.com/fe-platform/61708b29215d3fcb6790d25c061d47775823d379.png",
        "[折叠车R]" to "https://fe-video-qc.xhscdn.com/fe-platform/57f9b02650b95f08122c0462927cce3df847e246.png",
        "[飞盘R]" to "https://fe-video-qc.xhscdn.com/fe-platform/6e92309ecab879d8bb1b0b83536f025bdc1e21e8.png",
        "[冲浪板R]" to "https://fe-video-qc.xhscdn.com/fe-platform/012c014ef465c0bda4a3af39a713629aa3508da3.png",
        "[双翘滑板R]" to "https://fe-video-qc.xhscdn.com/fe-platform/bf2be5bd7fe7b7aac5bc06c44ea2daf456750674.png",
        "[陆冲板R]" to "https://fe-video-qc.xhscdn.com/fe-platform/5a9b44f49a27f75224f6cbd3ef95ec65a579f907.png",
        "[长板R]" to "https://fe-video-qc.xhscdn.com/fe-platform/87d0aeb63f769b04eb119bd2f0fc9128a645747a.png",
        "[种草R]" to "https://picasso-static.xiaohongshu.com/fe-platform/035c8044c53dbf7df2cf28d6ec35eb325567121b.png",
        "[拔草R]" to "https://picasso-static.xiaohongshu.com/fe-platform/c9e8d66eabeaa823b91e4caeb62088a1521dbe63.png",
        "[点赞R]" to "https://picasso-static.xiaohongshu.com/fe-platform/391438d25580a034707791b5f165c27f8899025a.png",
        "[向右R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ae143d3423b5af03ae6b63dc197872ec6a59a6ff.png",
        "[合十R]" to "https://picasso-static.xiaohongshu.com/fe-platform/fbdbb2547a281e18ee9759e3d658d417871996c0.png",
        "[okR]" to "https://picasso-static.xiaohongshu.com/fe-platform/65bce6a5e07c5adecd8a9660f833266c4cffa0e6.png",
        "[加油R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ab059229949e73619961c5ee1f7ee10d2318c170.png",
        "[握手R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d0d01ced40255c3855c80fc641b432758c041dea.png",
        "[鼓掌R]" to "https://picasso-static.xiaohongshu.com/fe-platform/59bbbe6fc2879f6ef42e63b3264096a9f4d403c7.png",
        "[弱R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ab298d8a629530f3bb98b94718acb6f20b2cbc66.png",
        "[耶R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b7d3bb36a6422f92f2447f2b300d3aff0b7baa21.png",
        "[抱拳R]" to "https://picasso-static.xiaohongshu.com/fe-platform/0ae972c2da43acd565596fb0234c558f84b0a390.png",
        "[勾引R]" to "https://picasso-static.xiaohongshu.com/fe-platform/0b219f805826238b85eb114bb1781bf5d5808cbf.png",
        "[拳头R]" to "https://picasso-static.xiaohongshu.com/fe-platform/20bb351c9538975e1a3b8ec4aa5821ad9d6f2215.png",
        "[拥抱R]" to "https://picasso-static.xiaohongshu.com/fe-platform/efc3b7a9e6df5d2be0233e203adf0d1110623441.png",
        "[举手R]" to "https://picasso-static.xiaohongshu.com/fe-platform/84320b00dda66dcb661b5fb5d75ded2de4754b0a.png",
        "[猪头R]" to "https://picasso-static.xiaohongshu.com/fe-platform/e7eae4ef972a29818a56d6e00f85304152a58430.png",
        "[老虎R]" to "https://picasso-static.xiaohongshu.com/fe-platform/f6d52ce0dd3bfa963a5a624e9da8417d02c9f752.png",
        "[集美R]" to "https://picasso-static.xiaohongshu.com/fe-platform/124387198d229cb5aa2be5dd74db4af820e85dcd/xhs_theme_xy_emotion_redmoji_jimei.png",
        "[仙女R]" to "https://picasso-static.xiaohongshu.com/fe-platform/3a0d4108b32e366f7438d448a8157e9e4247e5b3/xhs_theme_xy_emotion_redmoji_xiannv.png",
        "[红书R]" to "https://picasso-static.xiaohongshu.com/fe-platform/182d040c46942e0ba1c8eeb66bf7047dad751e72.png",
        "[开箱R]" to "https://picasso-static.xiaohongshu.com/fe-platform/200ada9354c5c974164bffa594ad4e33614404aa.png",
        "[探店R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b9dfa6d9e5cb81b2f0bdd77e14b1841608c03224.png",
        "[ootdR]" to "https://picasso-static.xiaohongshu.com/fe-platform/595650f7fb0ee6a475c6bdbe4d6a707524ed9c90.png",
        "[同款R]" to "https://picasso-static.xiaohongshu.com/fe-platform/1a573c081b4aad6814c23a33d51c86a69670b90f.png",
        "[打卡R]" to "https://picasso-static.xiaohongshu.com/fe-platform/89214fad0c95300ab58a96037fddafa0415d387e.png",
        "[飞机R]" to "https://picasso-static.xiaohongshu.com/fe-platform/9ac94463031f15e8c73db4a457a35ac473822a00.png",
        "[拍立得R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d87604b3ab8b56e98023ae582deea40230595fcc.png",
        "[薯券R]" to "https://picasso-static.xiaohongshu.com/fe-platform/080302ac0fd8f847753853c50cd0cf00709c4419.png",
        "[优惠券R]" to "https://picasso-static.xiaohongshu.com/fe-platform/68ef659532ab68296aa14f89e29829da4d9aed5a.png",
        "[购物车R]" to "https://picasso-static.xiaohongshu.com/fe-platform/3598e9b2a43cd1ca6ec4b4dc7670541c7bdda2fa.png",
        "[kissR]" to "https://picasso-static.xiaohongshu.com/fe-platform/071e9c9d731ce31f5ece64babda5f3d4d9207496.png",
        "[礼物R]" to "https://picasso-static.xiaohongshu.com/fe-platform/39e0ed44f24bd2d211161a5086705ab1d4439c41.png",
        "[生日蛋糕R]" to "https://picasso-static.xiaohongshu.com/fe-platform/259be907840312a7013dae79ff6f99012dabe24b.png",
        "[私信R]" to "https://picasso-static.xiaohongshu.com/fe-platform/2062069d03c2927cc823ad0f65c4db645e968058.png",
        "[请文明R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d070fee56c6069ac246ffb0cba1eaf3609df9680.png",
        "[请友好R]" to "https://picasso-static.xiaohongshu.com/fe-platform/5c4d2abd9058163b496e054d7448d91c212282d3.png",
        "[氛围感R]" to "https://picasso-static.xiaohongshu.com/fe-platform/acad9319c8ad606833872094506ebbfffd321344.png",
        "[清单R]" to "https://picasso-static.xiaohongshu.com/fe-platform/20eab20210e0958b0da33174b7f4606eca92b92b.png",
        "[电影R]" to "https://picasso-static.xiaohongshu.com/fe-platform/3eec7a10e8cf68f44dbcb930ecb05f2927f8ae1e.png",
        "[学生党R]" to "https://picasso-static.xiaohongshu.com/fe-platform/04984e414827730e5689900e1e45d3fd0c50a6d6.png",
        "[彩虹R]" to "https://picasso-static.xiaohongshu.com/fe-platform/5862336b380dc7bd68f068e19b8ef613b7913c3d.png",
        "[爆炸R]" to "https://picasso-static.xiaohongshu.com/fe-platform/58ed0344253015243334e5b1fd6b642ee3e0346c.png",
        "[炸弹R]" to "https://picasso-static.xiaohongshu.com/fe-platform/403d2c9ede2e95cb8b82dd348da4b2aac0bf9d62.png",
        "[火R]" to "https://picasso-static.xiaohongshu.com/fe-platform/51f1d8e7c5b4182c05510f3aeadecee19e968b42.png",
        "[啤酒R]" to "https://picasso-static.xiaohongshu.com/fe-platform/9e71d86b28f1ba48b58291b53bf6156810fb9377.png",
        "[咖啡R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b3b5dbb3a564a68115a4343fe536a20e34d3c953.png",
        "[钱袋R]" to "https://picasso-static.xiaohongshu.com/fe-platform/026f431acf58d6d2a19963a68dbf70c53359eada.png",
        "[流汗R]" to "https://picasso-static.xiaohongshu.com/fe-platform/4fc14b31e947deec15d0a1b3f96ae57214ab2bb2.png",
        "[发R]" to "https://picasso-static.xiaohongshu.com/fe-platform/8a61d522a0a19e51280b780af24d2cf972195d24.png",
        "[红包R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d708e5bb8b0d5e1a0628a3e2324bfde507736f1c.png",
        "[福R]" to "https://picasso-static.xiaohongshu.com/fe-platform/7d0da07b800a4b999e06ce66759336be05f3f3a0.png",
        "[鞭炮R]" to "https://picasso-static.xiaohongshu.com/fe-platform/3415b947b0b66b01c4fabdec2b729c34a5f8a0b2.png",
        "[庆祝R]" to "https://picasso-static.xiaohongshu.com/fe-platform/51eab29d66493ab028e9a446c6c10fa606e1e412.png",
        "[烟花R]" to "https://picasso-static.xiaohongshu.com/fe-platform/64071df3b7c40545149a1d26fcfdf0e704c96c2c.png",
        "[气球R]" to "https://picasso-static.xiaohongshu.com/fe-platform/a57b1e6f8e48ac2a4171afe620df545dd760fd08.png",
        "[看R]" to "https://picasso-static.xiaohongshu.com/fe-platform/f3c0659718c26f36ca3d57466c9cc0a9120e52f8.png",
        "[新月R]" to "https://picasso-static.xiaohongshu.com/fe-platform/a1493a29d6a4b63caa73a2a2af4706186dbccd6b.png",
        "[满月R]" to "https://picasso-static.xiaohongshu.com/fe-platform/bf117e6b7458e3bec281b34d9ed767aed94cdc40.png",
        "[大便R]" to "https://picasso-static.xiaohongshu.com/fe-platform/82e3b1495613b1c173c8a5d4efcd9cc32ecfb6b9.png",
        "[太阳R]" to "https://picasso-static.xiaohongshu.com/fe-platform/fe0276430f14dad6b791528ba3acd0c541998a28.png",
        "[晚安R]" to "https://picasso-static.xiaohongshu.com/fe-platform/937f70403d7a0b65d0b42fcd67e0efd8618c3d05.png",
        "[星R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b98fbe9d7371faf3ff43342f166297cf6446531d.png",
        "[玫瑰R]" to "https://picasso-static.xiaohongshu.com/fe-platform/abc0a1cd8434c5348e89e887cf8a4f93f352558c.png",
        "[凋谢R]" to "https://picasso-static.xiaohongshu.com/fe-platform/5f58213013b6d97a190fc42b1e2aed344e746ba3.png",
        "[郁金香R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ee78f61c5c20e159e97bee4612bc2089c358f33b.png",
        "[樱花R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ef50e51cb37c948b56dc856fed12e5643597c1dc.png",
        "[海豚R]" to "https://picasso-static.xiaohongshu.com/fe-platform/b1a4ebde71f735db6c2f45dfce4e23126fc28c32.png",
        "[放大镜R]" to "https://picasso-static.xiaohongshu.com/fe-platform/257c99be653d2ccc3f25b7426aa1e5a269e85421.png",
        "[刀R]" to "https://picasso-static.xiaohongshu.com/fe-platform/a4d581be51146d70d81679d603d579da040e7183.png",
        "[辣椒R]" to "https://picasso-static.xiaohongshu.com/fe-platform/9ad29f04bb78c2551f3e5d57425618a78455b20e.png",
        "[黄瓜R]" to "https://picasso-static.xiaohongshu.com/fe-platform/c15e57a392c37774bfa119af17cfc4f1c5b9ec70.png",
        "[葡萄R]" to "https://picasso-static.xiaohongshu.com/fe-platform/5978958778577a9baa16b93cc0979d9d70291919.png",
        "[草莓R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d29f5474efafbe34835214c37c42f6159fbba789.png",
        "[桃子R]" to "https://picasso-static.xiaohongshu.com/fe-platform/4d64f9e067d75a9722f46d8f858d7afbb43908ed.png",
        "[红薯R]" to "https://picasso-static.xiaohongshu.com/fe-platform/bfb8a6309b8b42af2cf7c8ce20d1d4fb9a64b512.png",
        "[栗子R]" to "https://picasso-static.xiaohongshu.com/fe-platform/3160dda81f09abd55fc26312a53f5945cd975834.png",
        "[红色心形R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d6125900d5de3969a1bb075e23d361c4bd78b0eb.png",
        "[黄色心形R]" to "https://picasso-static.xiaohongshu.com/fe-platform/5421d25d7566afe3fbd5a91c9e704ea2afa4a639.png",
        "[绿色心形R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d384e2e381f4c96257b29ccc054d70d82af786f7.png",
        "[蓝色心形R]" to "https://picasso-static.xiaohongshu.com/fe-platform/284e12f435d3c09056dd264384adbdbb82833c15.png",
        "[紫色心形R]" to "https://picasso-static.xiaohongshu.com/fe-platform/ca6e9a1c66a32bd7f2c5c49f1b51507c8f16c902.png",
        "[爱心R]" to "https://picasso-static.xiaohongshu.com/fe-platform/fc7cec55e0e1a0ffd8668d89ea2921c23c63539e.png",
        "[两颗心R]" to "https://picasso-static.xiaohongshu.com/fe-platform/58b58fa86c33cf358b83aef0e5c9a89298cbc1e4.png",
        "[浅肤色R]" to "https://picasso-static.xiaohongshu.com/fe-platform/691d1d3544521be6fa0ffbf58d6a9743d5303a16.png",
        "[中浅肤色R]" to "https://picasso-static.xiaohongshu.com/fe-platform/573a26c25f11bacad6a6e266833fdf21fe893e17.png",
        "[中等肤色R]" to "https://picasso-static.xiaohongshu.com/fe-platform/e24ca827231348b427b5b3e0b0c6675f9eced27b.png",
        "[中深肤色R]" to "https://picasso-static.xiaohongshu.com/fe-platform/414cc459c8d22b93b79e97b76b0f4a906557c564.png",
        "[有R]" to "https://picasso-static.xiaohongshu.com/fe-platform/6c4ed27842a186f3a89a65f74cc9b3984e12e5e6.png",
        "[可R]" to "https://picasso-static.xiaohongshu.com/fe-platform/1901af71ad54c620e4c2d895fb6a2af28cd83ca5.png",
        "[蹲R]" to "https://picasso-static.xiaohongshu.com/fe-platform/682af0d49dcf04c340abff12b81558621850b900.png",
        "[零R]" to "https://picasso-static.xiaohongshu.com/fe-platform/51f0fc07ddd7d44751b41d53f102114fd7255881.png",
        "[一R]" to "https://picasso-static.xiaohongshu.com/fe-platform/1f6bad36efca7e77f20e5c0339c44564cf0a6fa0.png",
        "[二R]" to "https://picasso-static.xiaohongshu.com/fe-platform/bdb8a0f60e918177ee4de71aebced4a68658f545.png",
        "[三R]" to "https://picasso-static.xiaohongshu.com/fe-platform/f41145ef41eaf9f8d42e208cace1f2a0f9ed602b.png",
        "[四R]" to "https://picasso-static.xiaohongshu.com/fe-platform/9e3c5dc71bee8d45b9be5ffe63554abf86512fe1.png",
        "[五R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d8c24a51ffbe618a13fc19748e0d4e7cf80dba78.png",
        "[六R]" to "https://picasso-static.xiaohongshu.com/fe-platform/55962ff13b3cb8cc3388d5acd8627d8aa40b8fb8.png",
        "[七R]" to "https://picasso-static.xiaohongshu.com/fe-platform/4d19093baf638f86987d9ccb9f530060b573d5a0.png",
        "[八R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d245ba7b1bdc7f73928e282194acc654b10a3bbb.png",
        "[九R]" to "https://picasso-static.xiaohongshu.com/fe-platform/bdd4d21ae715040c7afb737317797266ef14f727.png",
        "[加一R]" to "https://picasso-static.xiaohongshu.com/fe-platform/d5f1bbb77a939d7521ebe80439b39a77f05310ff.png",
        "[满R]" to "https://picasso-static.xiaohongshu.com/fe-platform/6775ba4a34325edc384a932c5aa9ff4b7be059d4.png",
        "[禁R]" to "https://picasso-static.xiaohongshu.com/fe-platform/f168e3aa080bff213e57b5b8367b4fb161e99ce8.png",
    )

private val xhsEmojiMap = xhsStaticEmojiMap + xhsDynamicEmojiMap

internal fun XhsFeedItem.hasDetailedMedia(): Boolean =
    noteCard?.let { card ->
        card.imageList.isNotEmpty() || card.video?.hasPlayableStream() == true
    } == true

internal fun XhsFeedItem.toUiTimeline(accountKey: MicroBlogKey): UiTimelineV2.Post? {
    val card = noteCard ?: return null
    val noteId = card.noteId?.takeIf { it.isNotBlank() } ?: id.takeIf { it.isNotBlank() } ?: return null
    xsecToken?.takeIf { it.isNotBlank() }?.let { token ->
        XhsNoteContextCache.put(
            XhsNoteContext(
                noteId = noteId,
                xsecToken = token,
                xsecSource = xsecSource ?: "pc_feed",
            ),
        )
    }
    return card.toUiTimeline(accountKey, noteId)
}

internal fun XhsNoteCard.toUiTimeline(
    accountKey: MicroBlogKey,
    noteId: String,
): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(noteId, accountKey.host)
    xsecToken?.takeIf { it.isNotBlank() }?.let { token ->
        XhsNoteContextCache.put(
            XhsNoteContext(
                noteId = noteId,
                xsecToken = token,
                xsecSource = xsecSource ?: "pc_profile",
            ),
        )
    }
    val sourceLanguages = persistentListOf<String>()
    val title = displayTitle?.takeIf { it.isNotBlank() } ?: title.orEmpty()
    val text =
        buildString {
            if (title.isNotBlank()) {
                append(title)
            }
            desc?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n\n")
                append(it)
            }
        }
    val media: List<UiMedia> =
        (
            if (type == "video") {
                val fallbackCover = cover ?: imageList.firstOrNull()
                listOfNotNull(video?.toUiMedia(fallbackCover) ?: fallbackCover?.toVideoPlaceholder())
            } else {
                imageList
                    .mapNotNull { it.toUiMedia() }
                    .ifEmpty { listOfNotNull(cover?.toUiMedia()) }
            }
        ).distinctBy { it.mediaIdentity() }
    val createdAt =
        normalizeXhsTimestamp(
            firstPositive(
                createTime,
                timestamp,
                lastUpdateTime,
                updateTime,
                time,
                noteId.xhsObjectIdEpochSeconds(),
            ),
        ).toUi()
    val actions =
        buildList {
            interactInfo?.commentCount?.let { count ->
                add(
                    ActionMenu.Item(
                        icon = UiIcon.Reply,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                        count = UiNumber(count.toCount()),
                    ),
                )
            }
            interactInfo?.likedCount?.let { count ->
                add(
                    ActionMenu.Item(
                        icon = UiIcon.Like,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                        count = UiNumber(count.toCount()),
                    ),
                )
            }
            interactInfo?.collectedCount?.let { count ->
                add(
                    ActionMenu.Item(
                        icon = UiIcon.Bookmark,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Bookmark),
                        count = UiNumber(count.toCount()),
                    ),
                )
            }
            add(
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        persistentListOf(
                            ActionMenu.Item(
                                icon = UiIcon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status.ShareSheet(
                                            statusKey = statusKey,
                                            accountType = AccountType.Specific(accountKey),
                                            shareUrl = "https://www.xiaohongshu.com/explore/$noteId",
                                        ),
                                    ),
                            ),
                        ),
                ),
            )
        }
    return UiTimelineV2.Post(
        message =
            if (interactInfo?.isSticky() == true) {
                UiTimelineV2.Message(
                    user = user?.toUiProfile(accountKey),
                    icon = UiIcon.Pin,
                    type =
                        UiTimelineV2.Message.Type.Localized(
                            UiTimelineV2.Message.Type.Localized.MessageId.Pinned,
                        ),
                    statusKey = statusKey,
                    createdAt = createdAt,
                    clickEvent = ClickEvent.Noop,
                    accountType = AccountType.Specific(accountKey),
                )
            } else {
                null
            },
        platformType = PlatformType.Xiaohongshu,
        images = media.toPersistentList(),
        sensitive = false,
        contentWarning = null,
        user = user?.toUiProfile(accountKey),
        sourceLanguages = sourceLanguages,
        quote = persistentListOf(),
        content =
            text.toXhsRichText(accountKey, sourceLanguages),
        actions = actions.toPersistentList(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = createdAt,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = statusKey,
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun XhsComment.toUiTimeline(
    accountKey: MicroBlogKey,
    noteId: String,
    includeInlineSubComments: Boolean = true,
): UiTimelineV2.Post {
    val id = commentId?.takeIf { it.isNotBlank() } ?: id
    val statusKey = MicroBlogKey("$noteId:comment:$id", accountKey.host)
    val sourceLanguages = persistentListOf<String>()
    val replyTo = targetComment?.userInfo?.displayName()?.takeIf { it.isNotBlank() }
    val text =
        buildString {
            if (!replyTo.isNullOrBlank()) {
                append("@")
                append(replyTo)
                append(" ")
            }
            append(content)
        }
    val rootCommentId = targetComment?.mapperCommentIdentity()?.takeIf { it.isNotBlank() }
    val threadStatusKey =
        rootCommentId
            ?.let { MicroBlogKey("$noteId:comment:$it", accountKey.host) }
            ?: statusKey
    val detailStatusKey =
        if (rootCommentId != null || subCommentCount.toCount() > 0L) {
            threadStatusKey
        } else {
            MicroBlogKey(noteId, accountKey.host)
        }
    return UiTimelineV2.Post(
        platformType = PlatformType.Xiaohongshu,
        images = media().toPersistentList(),
        sensitive = false,
        contentWarning = null,
        user = userInfo?.toUiProfile(accountKey),
        sourceLanguages = sourceLanguages,
        quote =
            if (includeInlineSubComments) {
                subComments
                    .map { it.toUiTimeline(accountKey, noteId, includeInlineSubComments = false) }
                    .toPersistentList()
            } else {
                persistentListOf()
            },
        content =
            text.toXhsRichText(accountKey, sourceLanguages),
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(subCommentCount.toCount()),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                accountType = AccountType.Specific(accountKey),
                                statusKey = detailStatusKey,
                            ),
                        ),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                    count = UiNumber(likeCount.toCount()),
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = normalizeXhsTimestamp(createTime).toUi(),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = detailStatusKey,
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

private fun XhsComment.mapperCommentIdentity(): String =
    commentId?.takeIf { it.isNotBlank() } ?: id

private fun XhsUser.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val name = displayName()
    val userId = firstNotBlank(userId, redId, name)
    val displayHandle = name.ifBlank { userId }
    val userKey = MicroBlogKey(userId, accountKey.host)
    return UiProfile(
        key = userKey,
        handle = UiHandle(displayHandle, "xhs"),
        avatar = firstNotBlank(avatar, image, images),
        nameInternal = name.ifBlank { userId }.toUiPlainText(),
        platformType = PlatformType.Xiaohongshu,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = null,
        matrices = UiProfile.Matrices(0L, 0L, 0L),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun XhsUser.displayName(): String = firstNotBlank(nickname, nickName, userId)

internal fun XhsUserMe.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val userId = userId.ifBlank { accountKey.id }
    val displayHandle = nickname.ifBlank { userId }
    val userKey = MicroBlogKey(userId, accountKey.host)
    return UiProfile(
        key = userKey,
        handle = UiHandle(displayHandle, "xhs"),
        avatar = image ?: images.orEmpty(),
        nameInternal = nickname.ifBlank { userId }.toUiPlainText(),
        platformType = PlatformType.Xiaohongshu,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = null,
        matrices = UiProfile.Matrices(0L, 0L, 0L),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal fun XhsSearchUser.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val userId = firstNotBlank(userId, id, redId, nickname, userNickname, nickName, name)
    val nickname = firstNotBlank(nickname, userNickname, nickName, name, userId)
    val userKey = MicroBlogKey(userId, accountKey.host)
    return UiProfile(
        key = userKey,
        handle = UiHandle(nickname.ifBlank { userId }, "xhs"),
        avatar = firstNotBlank(avatar, userAvatar, image, images),
        nameInternal = nickname.ifBlank { userId }.toUiPlainText(),
        platformType = PlatformType.Xiaohongshu,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = desc.orEmpty().toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount =
                    firstPositiveCount(
                        fans?.toCount(),
                        fansCount?.toCount(),
                        followers?.toCount(),
                        followersCount?.toCount(),
                        fansTotal?.toCount(),
                    ),
                followsCount = firstPositiveCount(follows?.toCount(), followCount?.toCount(), followsTotal?.toCount()),
                statusesCount = firstPositiveCount(notes?.toCount(), noteCount?.toCount(), noteTotal?.toCount()),
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal fun XhsUserInfoData.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    return toUiProfile(accountKey = accountKey, requestedUserId = null)
}

internal fun XhsUserInfoData.toUiProfile(
    accountKey: MicroBlogKey,
    requestedUserId: String?,
): UiProfile {
    val basic = basicInfo
    val userId = firstNotBlank(basic?.userId, userId, requestedUserId, basic?.redId, redId)
    val nickname = firstNotBlank(basic?.nickname, basic?.nickName, nickname, userId)
    val userKey = MicroBlogKey(userId, accountKey.host)
    val fansCount =
        firstPositiveCount(
            interactions.countByLabels("fans", "fan", "fans_total", "followers", "follower", "粉丝"),
            fans.toCount(),
            fansTotal.toCount(),
            fansCount.toCount(),
            followers.toCount(),
            followersCount.toCount(),
            basic?.fans?.toCount(),
            basic?.fansTotal?.toCount(),
            basic?.fansCount?.toCount(),
            basic?.followers?.toCount(),
            basic?.followersCount?.toCount(),
        )
    val followsCount =
        firstPositiveCount(
            interactions.countByLabels("follows", "follow", "following", "关注"),
            follows.toCount(),
            follow.toCount(),
            followCount.toCount(),
            following.toCount(),
            followingCount.toCount(),
            basic?.follows?.toCount(),
            basic?.follow?.toCount(),
            basic?.followCount?.toCount(),
            basic?.following?.toCount(),
            basic?.followingCount?.toCount(),
        )
    val statusesCount =
        firstPositiveCount(
            interactions.countByLabels("notes", "note", "posts", "post", "works", "笔记", "作品"),
            notes.toCount(),
            noteCount.toCount(),
            notesCount.toCount(),
            postedNoteCount.toCount(),
            postedNotesCount.toCount(),
            basic?.notes?.toCount(),
            basic?.noteCount?.toCount(),
            basic?.notesCount?.toCount(),
            basic?.postedNoteCount?.toCount(),
            basic?.postedNotesCount?.toCount(),
        )
    return UiProfile(
        key = userKey,
        handle = UiHandle(nickname.ifBlank { userId }, "xhs"),
        avatar = firstNotBlank(basic?.avatar, basic?.image, basic?.images),
        nameInternal = nickname.ifBlank { userId }.toUiPlainText(),
        platformType = PlatformType.Xiaohongshu,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = firstNotBlank(basic?.desc, desc).toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = fansCount,
                followsCount = followsCount,
                statusesCount = statusesCount,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun XhsImage.toUiMedia(): UiMedia.Image? {
    val url = bestUrl().takeIf { it.isNotBlank() } ?: return null
    return UiMedia.Image(
        url = url,
        previewUrl = previewUrl().takeIf { it.isNotBlank() } ?: url,
        description = null,
        height = height?.toFloat() ?: 1f,
        width = width?.toFloat() ?: 1f,
        sensitive = false,
        customHeaders = xhsMediaHeaders,
    )
}

private fun XhsVideo.toUiMedia(fallbackCover: XhsImage? = null): UiMedia.Video? {
    val stream = media?.stream
    val item =
        stream?.h264?.firstWithUrl()
            ?: stream?.h265?.firstWithUrl()
            ?: stream?.h266?.firstWithUrl()
            ?: stream?.av1?.firstWithUrl()
    val url = item?.bestUrl()?.takeIf { it.isNotBlank() } ?: return null
    return UiMedia.Video(
        url = url,
        thumbnailUrl = firstNotBlank(cover?.bestUrl(), fallbackCover?.bestUrl()),
        description = null,
        height = item.height?.toFloat() ?: cover?.height?.toFloat() ?: 9f,
        width = item.width?.toFloat() ?: cover?.width?.toFloat() ?: 16f,
        customHeaders = xhsMediaHeaders,
    )
}

private fun XhsImage.toVideoPlaceholder(): UiMedia.Video? {
    val thumbnail = bestUrl().takeIf { it.isNotBlank() } ?: return null
    return UiMedia.Video(
        url = "",
        thumbnailUrl = thumbnail,
        description = null,
        height = height?.toFloat() ?: 9f,
        width = width?.toFloat() ?: 16f,
        customHeaders = xhsMediaHeaders,
    )
}

private fun XhsVideo.hasPlayableStream(): Boolean {
    val stream = media?.stream ?: return false
    return stream.h264.firstWithUrl() != null ||
        stream.h265.firstWithUrl() != null ||
        stream.h266.firstWithUrl() != null ||
        stream.av1.firstWithUrl() != null
}

private fun XhsComment.media(): List<UiMedia> =
    (imageList.orEmpty() + pictures.orEmpty() + listOfNotNull(image))
        .mapNotNull { it.toUiMedia() }
        .distinctBy { it.mediaIdentity() }

private fun XhsInteractInfo.isSticky(): Boolean =
    sticky.equals("true", ignoreCase = true) || sticky == "1"

private fun UiMedia.mediaIdentity(): String =
    when (this) {
        is UiMedia.Image -> previewUrl
        is UiMedia.Video -> url
        is UiMedia.Gif -> url
        is UiMedia.Audio -> url
    }

private fun List<dev.dimension.flare.data.network.xiaohongshu.model.XhsVideoStreamItem>.firstWithUrl() =
    firstOrNull { it.bestUrl().isNotBlank() }

private fun dev.dimension.flare.data.network.xiaohongshu.model.XhsVideoStreamItem.bestUrl(): String =
    firstNotBlank(masterUrl, url, backupUrls.firstOrNull())
        .normalizeXhsMediaUrl()

private fun XhsImage.bestUrl(): String =
    firstNotBlank(
        infoList.firstBestUrl(),
        urlDefault,
        urlPre,
        url,
        urlList.firstOrNull(),
    )
        .normalizeXhsMediaUrl()

private fun XhsImage.previewUrl(): String =
    firstNotBlank(
        urlPre,
        infoList.firstPreviewUrl(),
        urlDefault,
        url,
        urlList.firstOrNull(),
        infoList.firstBestUrl(),
    )
        .normalizeXhsMediaUrl()

private fun firstNotBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

private fun List<dev.dimension.flare.data.network.xiaohongshu.model.XhsImageInfo>.firstBestUrl(): String? =
    firstOrNull { it.imageScene == "WB_DFT" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { it.imageScene == "FD_WM_WEBP" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { it.imageScene == "FD_PRV_WEBP" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { !it.url.isNullOrBlank() }?.url

private fun List<dev.dimension.flare.data.network.xiaohongshu.model.XhsImageInfo>.firstPreviewUrl(): String? =
    firstOrNull { it.imageScene == "WB_PRV" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { it.imageScene == "FD_PRV_WEBP" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { it.imageScene == "WB_DFT" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { it.imageScene == "FD_WM_WEBP" && !it.url.isNullOrBlank() }?.url
        ?: firstOrNull { !it.url.isNullOrBlank() }?.url

private fun String.normalizeXhsMediaUrl(): String =
    if (startsWith("http://", ignoreCase = true)) {
        replaceFirst("http://", "https://", ignoreCase = true)
    } else {
        this
    }

private fun normalizeXhsTimestamp(value: Long): Instant {
    val timestamp = value.takeIf { it > 0L } ?: return Instant.fromEpochMilliseconds(0L)
    return when {
        timestamp < 10_000_000_000L -> Instant.fromEpochSeconds(timestamp)
        timestamp < 10_000_000_000_000L -> Instant.fromEpochMilliseconds(timestamp)
        timestamp < 10_000_000_000_000_000L -> Instant.fromEpochMilliseconds(timestamp / 1_000L)
        else -> Instant.fromEpochMilliseconds(timestamp / 1_000_000L)
    }
}

private fun String.toXhsRichText(
    accountKey: MicroBlogKey,
    sourceLanguages: List<String>,
): UiRichText {
    val matches = xhsInlineTokenRegex.findAll(this).toList()
    if (matches.isEmpty()) {
        return toUiPlainText(sourceLanguages)
    }
    val runs = mutableListOf<RenderRun>()
    var cursor = 0
    fun appendText(value: String) {
        if (value.isNotEmpty()) {
            runs.add(RenderRun.Text(value))
        }
    }
    matches.forEach { match ->
        if (match.range.first > cursor) {
            appendText(substring(cursor, match.range.first))
        }
        val value = match.value
        val topic = match.groups[1]?.value?.trim().orEmpty()
        val emojiUrl = xhsEmojiMap[value]
        when {
            topic.isNotEmpty() -> {
                val text = "#$topic#"
                runs.add(
                    RenderRun.Text(
                        text = text,
                        style =
                            RenderTextStyle(
                                link =
                                    DeeplinkRoute
                                        .Search(AccountType.Specific(accountKey), text)
                                        .toUri(),
                            ),
                    ),
                )
            }
            emojiUrl != null -> runs.add(RenderRun.Image(url = emojiUrl, alt = value))
            else -> appendText(value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        appendText(substring(cursor))
    }
    return uiRichTextOf(
        listOf(
            RenderContent.Text(
                runs = runs.toImmutableList(),
            )
        ),
        raw = this,
        innerText = this,
        sourceLanguages = sourceLanguages,
    )
}

private fun firstPositive(vararg values: Long): Long = values.firstOrNull { it > 0L } ?: 0L

private fun String.xhsObjectIdEpochSeconds(): Long =
    takeIf { it.length >= 8 && it.take(8).all { char -> char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F' } }
        ?.let { id ->
            runCatching { id.take(8).toLong(16) }.getOrNull()
        }?.takeIf { seconds ->
            seconds in 946_684_800L..4_102_444_800L
        } ?: 0L

private fun firstPositiveCount(vararg values: Long?): Long = values.firstOrNull { it != null && it > 0L } ?: 0L

private fun List<dev.dimension.flare.data.network.xiaohongshu.model.XhsUserInteraction>.countByLabels(
    vararg labels: String,
): Long? {
    val normalizedLabels = labels.map { it.lowercase() }
    return firstNotNullOfOrNull { interaction ->
        val names =
            listOf(
                interaction.type,
                interaction.name,
                interaction.title,
            ).map { it.lowercase() }
        if (names.any { name -> normalizedLabels.any { label -> name == label || name.contains(label) } }) {
            interaction.count.toCount().takeIf { it > 0L }
        } else {
            null
        }
    }
}

private fun String?.toCount(): Long =
    this?.trim()?.let { value ->
        val number =
            value
                .filter { it.isDigit() || it == '.' }
                .toDoubleOrNull()
                ?: return@let null
        val multiplier =
            when {
                "万" in value -> 10_000.0
                "k" in value.lowercase() -> 1_000.0
                else -> 1.0
            }
        (number * multiplier).toLong()
    } ?: 0L
