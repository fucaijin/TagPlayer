package remix.myplayer.i18n

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import remix.myplayer.R
import remix.myplayer.ui.dialog.DialogState
import remix.myplayer.ui.dialog.NormalDialog
import remix.myplayer.ui.dialog.rememberDialogState
import remix.myplayer.ui.theme.LocalTheme
import remix.myplayer.ui.theme.highLightText
import remix.myplayer.ui.widget.common.CommonAppBar
import remix.myplayer.ui.widget.common.TextPrimary
import remix.myplayer.ui.widget.common.TextSecondary
import remix.myplayer.util.ext.clickWithRipple

/**
 * 语言设置界面：语言列表（单选）+ 导出/导入模板 + AI 翻译提示词弹窗 + 删除导入语言。
 *
 * 风格与其它设置页保持一致：顶栏用 [CommonAppBar]，背景/文字取自应用主题 [LocalTheme]
 * （MaterialTheme 的 colorScheme 恒为浅色，直接用会导致深色模式下黑字黑底）。
 *
 * 必须处于 `CompositionLocalProvider(LocalStringProvider provides ...)` 之下使用。
 */
@Composable
fun LanguageScreen(
  onBack: () -> Unit,
) {
  val t = LocalStringProvider.current
  val context = LocalContext.current
  var languages by remember { mutableStateOf(LocaleManager.listLanguages(context)) }
  var activeTag by remember { mutableStateOf(LocaleManager.activeTag(context)) }
  var pendingDelete by remember { mutableStateOf<String?>(null) }
  var importProblems by remember { mutableStateOf(emptyList<String>()) }

  val deleteDialogState = rememberDialogState()
  val promptDialogState = rememberDialogState()
  val invalidDialogState = rememberDialogState()

  // 本界面自身的显示语言：跟随「当前实际生效的语言」，而不是偏好里的 tag
  // （tag 可能是 system，也可能是没有资源目录的导入语言）
  val uiLang = remember(activeTag) {
    if (activeTag == LocaleManager.SYSTEM) Locale.getDefault().language else activeTag
  }
  val ui = remember(uiLang) { ScreenStrings.forLanguage(uiLang) }

  /** 选中一门语言：写入偏好 → 同步整个进程 → 重建 Activity 让 Compose 重新读取资源。 */
  fun select(tag: String) {
    if (tag == activeTag) return
    activeTag = tag
    LocaleManager.setActiveAndApply(context, tag)
    languages = LocaleManager.listLanguages(context)
    context.findActivity()?.recreate()
  }

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    if (uri != null) {
      val ok = runCatching {
        context.contentResolver.openOutputStream(uri)?.use {
          it.write(LocaleManager.exportTemplate(context).toByteArray())
        } != null
      }.getOrDefault(false)
      if (ok) {
        Toast.makeText(
          context,
          t.getOr("export_success", ui.exportSuccess),
          Toast.LENGTH_SHORT,
        ).show()
      } else {
        Toast.makeText(
          context,
          t.getOr("export_failed", ui.exportFailed),
          Toast.LENGTH_SHORT,
        ).show()
      }
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      val language: LocaleManager.ImportedLanguage? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use {
          it.readBytes().decodeToString()
        } ?: return@runCatching null
        LocaleManager.parseTemplate(text)
      }.getOrNull()
      // 占位符被改写会导致 String.format 失败、界面上直接露出 %1$d，导入前先拦一道
      val problems = language?.let { LocaleManager.validateTranslation(it) }.orEmpty()
      when {
        language == null -> {
          Toast.makeText(
            context,
            t.getOr("import_failed", ui.importFailed),
            Toast.LENGTH_SHORT,
          ).show()
        }

        problems.isNotEmpty() -> {
          importProblems = problems
          invalidDialogState.show()
        }

        else -> {
          val ok = runCatching { LocaleManager.importLanguage(context, language) }.isSuccess
          Toast.makeText(
            context,
            if (ok) t.getOr("import_success", ui.importSuccess)
            else t.getOr("import_failed", ui.importFailed),
            Toast.LENGTH_SHORT,
          ).show()
          if (ok) languages = LocaleManager.listLanguages(context)
        }
      }
    }
  }

  BackHandler { onBack() }

  Scaffold(
    topBar = {
      CommonAppBar(
        title = t.getOr("language", ui.title),
        onBack = onBack,
        actions = emptyList(),
      )
    },
    containerColor = LocalTheme.current.mainBackground,
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
      item {
        GuideCard(
          ui = ui,
          resolve = { key, fallback -> t.getOr(key, fallback) },
          onPrompt = { promptDialogState.show() },
        )
      }

      items(languages, key = { it.tag }) { option ->
        LanguageItem(
          option = option,
          activeTag = activeTag,
          systemDefault = t.getOr("system_default", ui.systemDefault),
          onSelect = { select(option.tag) },
          onDelete = {
            pendingDelete = option.tag
            deleteDialogState.show()
          },
        )
      }

      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ThemeOutlinedButton(
            text = t.getOr("export_template", ui.exportTemplate),
            modifier = Modifier.weight(1f),
            onClick = { exportLauncher.launch("strings_template.json") },
          )
          ThemeOutlinedButton(
            text = t.getOr("import_template", ui.importTemplate),
            modifier = Modifier.weight(1f),
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
          )
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }

  NormalDialog(
    dialogState = deleteDialogState,
    title = t.getOr("delete_language", ui.deleteLanguage),
    content = t.getOr("confirm_delete_language", ui.confirmDelete),
    positive = t.getOr("delete", ui.delete),
    onPositive = {
      val tag = pendingDelete ?: return@NormalDialog
      LocaleManager.deleteImported(context, tag)
      // 删掉的正好是当前语言时回退到「跟随系统」，否则会停在一条已经不存在的语言上
      if (activeTag == tag) {
        activeTag = LocaleManager.SYSTEM
        LocaleManager.setActiveAndApply(context, LocaleManager.SYSTEM)
        context.findActivity()?.recreate()
      }
      languages = LocaleManager.listLanguages(context)
      pendingDelete = null
    },
    negative = t.getOr("cancel", ui.cancel),
    onDismissRequest = { pendingDelete = null },
  )

  AiPromptDialog(
    dialogState = promptDialogState,
    ui = ui,
    uiLanguage = uiLang,
    resolve = { key, fallback -> t.getOr(key, fallback) },
  )

  // 译文校验不通过：列出有问题的条目，修正后才能导入
  NormalDialog(
    dialogState = invalidDialogState,
    title = t.getOr("import_invalid_title", ui.importInvalidTitle),
    content = buildString {
      append(t.getOr("import_invalid_message", ui.importInvalidMessage))
      append("\n\n")
      val shown = importProblems.take(MAX_PROBLEMS_SHOWN)
      append(shown.joinToString("\n"))
      val rest = importProblems.size - shown.size
      if (rest > 0) {
        append("\n")
        append(String.format(t.getOr("import_invalid_more", ui.importInvalidMore), rest))
      }
    },
    positive = t.getOr("confirm", ui.confirm),
    negative = null,
  )
}

/** 校验失败弹窗里最多列出的条目数，避免长列表把弹窗撑爆。 */
private const val MAX_PROBLEMS_SHOWN = 8

@Composable
private fun GuideCard(
  ui: ScreenStrings,
  resolve: (String, String) -> String,
  onPrompt: () -> Unit,
) {
  val theme = LocalTheme.current
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 16.dp),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = theme.libraryBackground),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      TextPrimary(
        text = resolve("language_guide_title", ui.guideTitle),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLine = Int.MAX_VALUE,
      )
      Spacer(Modifier.height(8.dp))
      GuideStep(resolve("guide_step_1", ui.step1))
      GuideStep(resolve("guide_step_2", ui.step2))
      GuideStep(resolve("guide_step_3", ui.step3))
      GuideStep(resolve("guide_step_4", ui.step4))
      Spacer(Modifier.height(12.dp))
      ThemeOutlinedButton(
        text = resolve("ai_translation_prompt", ui.aiPrompt),
        modifier = Modifier.fillMaxWidth(),
        onClick = onPrompt,
      )
    }
  }
}

@Composable
private fun GuideStep(text: String) {
  TextSecondary(
    text = text,
    modifier = Modifier.padding(vertical = 4.dp),
    fontSize = 14.sp,
    maxLine = Int.MAX_VALUE,
  )
}

@Composable
private fun LanguageItem(
  option: LocaleManager.LanguageOption,
  activeTag: String,
  systemDefault: String,
  onSelect: () -> Unit,
  onDelete: () -> Unit,
) {
  val theme = LocalTheme.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickWithRipple(false) { onSelect() }
      .background(color = theme.mainBackground)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
      RadioButton(
        selected = option.tag == activeTag,
        onClick = onSelect,
        colors = RadioButtonDefaults.colors(
          selectedColor = theme.primary,
          unselectedColor = theme.textSecondary,
          disabledSelectedColor = theme.textSecondary,
          disabledUnselectedColor = theme.textSecondary,
        ),
      )
    }
    TextPrimary(
      text = if (option.tag == LocaleManager.SYSTEM) systemDefault else option.displayName,
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp),
      fontSize = 16.sp,
    )
    if (option.imported) {
      IconButton(onClick = onDelete) {
        Icon(
          painter = painterResource(R.drawable.ic_delete_black_24dp),
          contentDescription = "DeleteLanguage",
          tint = theme.textSecondary,
        )
      }
    }
  }
}

/** 主题化的描边按钮：文字与描边跟随强调色，深色模式下也不会出现「黑字黑底」。 */
@Composable
private fun ThemeOutlinedButton(
  text: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val theme = LocalTheme.current
  val accent = theme.highLightText()
  OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    border = BorderStroke(1.dp, accent),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
  ) {
    Text(text = text, fontSize = 14.sp)
  }
}

@Composable
private fun AiPromptDialog(
  dialogState: DialogState,
  ui: ScreenStrings,
  uiLanguage: String,
  resolve: (String, String) -> String,
) {
  val theme = LocalTheme.current
  val context = LocalContext.current
  var targetLanguage by remember { mutableStateOf("") }
  val prompt = remember(uiLanguage, targetLanguage) {
    LocaleManager.buildAiPrompt(uiLanguage, targetLanguage)
  }

  NormalDialog(
    dialogState = dialogState,
    title = resolve("ai_translation_prompt", ui.aiPrompt),
    positive = resolve("copy_prompt", ui.copyPrompt),
    onPositive = {
      val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
      manager?.setPrimaryClip(ClipData.newPlainText("ai_prompt", prompt))
      Toast.makeText(
        context,
        resolve("prompt_copied", ui.promptCopied),
        Toast.LENGTH_SHORT,
      ).show()
    },
    negative = resolve("cancel", ui.cancel),
    custom = {
      OutlinedTextField(
        value = targetLanguage,
        onValueChange = { targetLanguage = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = theme.textPrimary),
        label = {
          Text(
            text = resolve("target_language_label", ui.targetLanguage),
            fontSize = 13.sp,
          )
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = theme.textPrimary,
          unfocusedTextColor = theme.textPrimary,
          focusedLabelColor = theme.textSecondary,
          unfocusedLabelColor = theme.textSecondary,
          cursorColor = theme.primary,
          focusedBorderColor = theme.primary,
          unfocusedBorderColor = theme.textSecondary,
        ),
      )
      Spacer(Modifier.height(12.dp))
      Column(
        modifier = Modifier
          .heightIn(max = 300.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        TextSecondary(
          text = prompt,
          fontSize = 13.sp,
          maxLine = Int.MAX_VALUE,
        )
      }
    },
  )
}

/** 从任意 Context 向上找到宿主 Activity，用于切换语言后重建界面。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

/** 界面自身的内置兜底文案（英文 / 中文），即使宿主未注册这些 key 也能正常显示。 */
private data class ScreenStrings(
  val title: String,
  val guideTitle: String,
  val step1: String,
  val step2: String,
  val step3: String,
  val step4: String,
  val aiPrompt: String,
  val targetLanguage: String,
  val copyPrompt: String,
  val promptCopied: String,
  val systemDefault: String,
  val exportTemplate: String,
  val importTemplate: String,
  val deleteLanguage: String,
  val confirmDelete: String,
  val delete: String,
  val cancel: String,
  val confirm: String,
  val importSuccess: String,
  val importFailed: String,
  val exportSuccess: String,
  val exportFailed: String,
  val importInvalidTitle: String,
  val importInvalidMessage: String,
  val importInvalidMore: String,
) {
  companion object {
    fun forLanguage(tag: String): ScreenStrings =
      if (tag.startsWith("zh")) ZH else EN

    private val EN = ScreenStrings(
      title = "Language",
      guideTitle = "Add a language with AI",
      step1 = "1. Tap \"Export template\" to save the template file.",
      step2 = "2. Tap \"AI translation prompt\" and copy the prompt.",
      step3 = "3. Send the prompt together with the template file to an AI, and tell it your target language.",
      step4 = "4. Save the AI result as a .json file, then tap \"Import template\".",
      aiPrompt = "AI translation prompt",
      targetLanguage = "Target language, e.g. Japanese, French",
      copyPrompt = "Copy prompt",
      promptCopied = "Prompt copied to clipboard",
      systemDefault = "System default",
      exportTemplate = "Export template",
      importTemplate = "Import template",
      deleteLanguage = "Delete language",
      confirmDelete = "Delete this language?",
      delete = "Delete",
      cancel = "Cancel",
      confirm = "OK",
      importSuccess = "Language imported",
      importFailed = "Failed to import language",
      exportSuccess = "Template exported",
      exportFailed = "Failed to export template",
      importInvalidTitle = "Translation not imported",
      importInvalidMessage = "Some strings are missing, or their placeholders do not match the source. Please fix the following and import again:",
      importInvalidMore = "...and %d more",
    )

    private val ZH = ScreenStrings(
      title = "语言",
      guideTitle = "借助 AI 添加语言",
      step1 = "1. 点击「导出模板」，保存模板文件。",
      step2 = "2. 点击「AI 翻译提示词」，复制提示词。",
      step3 = "3. 把提示词连同模板文件一起发给 AI，并告诉它目标语言。",
      step4 = "4. 将 AI 生成的结果保存为 .json 文件，再点击「导入模板」。",
      aiPrompt = "AI 翻译提示词",
      targetLanguage = "目标语言，如：日语、法语",
      copyPrompt = "复制提示词",
      promptCopied = "提示词已复制到剪贴板",
      systemDefault = "跟随系统",
      exportTemplate = "导出模板",
      importTemplate = "导入模板",
      deleteLanguage = "删除语言",
      confirmDelete = "删除此语言？",
      delete = "删除",
      cancel = "取消",
      confirm = "确定",
      importSuccess = "语言已导入",
      importFailed = "导入语言失败",
      exportSuccess = "模板已导出",
      exportFailed = "导出模板失败",
      importInvalidTitle = "译文未导入",
      importInvalidMessage = "以下字符串缺失，或其占位符与原文不一致（占位符必须原样保留，包括 %1${'$'}s、%1${'$'}d、%%）。请修正后重新导入：",
      importInvalidMore = "…另有 %d 项",
    )
  }
}
