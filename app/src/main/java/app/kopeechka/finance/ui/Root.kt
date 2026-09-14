package app.kopeechka.finance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.CurSheet
import app.kopeechka.finance.Page
import app.kopeechka.finance.Tab
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.ui.theme.LocalLang
import app.kopeechka.finance.ui.theme.T

@Composable
fun KopeechkaRoot(vm: AppViewModel, data: AppData, onEnableReminder: () -> Unit) {
    val lang = remember(data.settings.lang) { Lang.of(data.settings.lang) }
    CompositionLocalProvider(LocalLang provides lang) {
        val c = T.c
        val calc = remember(data, lang) { Calc(data, l = lang) }
        val onboarding = !data.settings.onboarded
        Box(
            Modifier
                .fillMaxSize()
                .background(if (onboarding) c.hero else c.bg)
                .systemBarsPadding()
                .imePadding(),
        ) {
            if (onboarding) {
                Onboarding(vm)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header(vm, calc)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (vm.tab) {
                            Tab.HOME -> HomeScreen(vm, calc)
                            Tab.OPS -> OpsScreen(vm, calc)
                            Tab.BUDGET -> BudgetScreen(vm, calc)
                            Tab.REPORT -> ReportScreen(vm, calc)
                            Tab.SETTINGS -> when (vm.page) {
                                null -> SettingsScreen(vm, calc, onEnableReminder)
                                Page.ACCOUNTS -> AccountsPage(vm, calc)
                                Page.CATEGORIES -> CategoriesPage(vm, calc)
                                Page.GOALS -> GoalsPage(vm, calc)
                                Page.BACKUP -> BackupPage(vm, calc)
                                Page.CURRENCIES -> CurrenciesPage(vm, calc)
                            }
                        }
                    }
                    BottomNav(vm)
                }

                vm.draft?.let { AddOverlay(vm, calc, it) }
                if (vm.advisorOpen) AdvisorOverlay(vm, data)
                if (vm.currencyPicker) CurrencyPickerOverlay(vm, calc)
                vm.accEdit?.let { AccEditOverlay(vm, calc, it) }
                vm.catEdit?.let { CatEditOverlay(vm, calc, it) }
                vm.goalEdit?.let { GoalEditOverlay(vm, it) }
                vm.curSheet?.let { CurrencySheet(vm, calc, it) }
                vm.goalSheet?.let { GoalContributeSheet(vm, calc, it) }
            }
            vm.confirm?.let { ConfirmSheet(vm, it) }
            vm.toast?.let {
                Toast(it, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 76.dp))
            }
        }
    }
}

@Composable
private fun Header(vm: AppViewModel, calc: Calc) {
    val c = T.c
    val l = T.l
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                l.t("app.name").uppercase(),
                style = T.h(15.sp, c.text, 0.14.em),
                modifier = Modifier.tapNoRipple { vm.go(Tab.HOME) },
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .hairline(c.divider)
                    .tap { vm.curSheet = CurSheet.Main }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("${calc.main} ${Currencies.sym(calc.main)}", style = T.h(11.sp, c.text, 0.12.em))
            }
            IconSquare(
                if (calc.s.dark) Icons.Sun else Icons.Moon,
                { vm.setDark(!calc.s.dark) },
                if (calc.s.dark) l.t("nav.themeLight") else l.t("nav.themeDark"),
            )
            IconSquare(Icons.Gear, { vm.go(Tab.SETTINGS) }, l.t("nav.settings"))
        }
        Divider()
    }
}

private enum class Glyph { PLAIN, LEFT, BOTTOM, TOP }

@Composable
private fun BottomNav(vm: AppViewModel) {
    val c = T.c
    val l = T.l
    Column(Modifier.background(c.bg)) {
        Divider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavItem(l.t("nav.home"), Glyph.PLAIN, vm.tab == Tab.HOME) { vm.go(Tab.HOME) }
            NavItem(l.t("nav.ops"), Glyph.LEFT, vm.tab == Tab.OPS) { vm.go(Tab.OPS) }
            Box(Modifier.weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(c.accent)
                        .hairline(c.a700)
                        .blueprintMarks(c.text.copy(alpha = 0.55f))
                        .tap { vm.openAdd() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = T.b(26.sp, Color.White))
                }
            }
            NavItem(l.t("nav.budget"), Glyph.BOTTOM, vm.tab == Tab.BUDGET) { vm.go(Tab.BUDGET) }
            NavItem(l.t("nav.reports"), Glyph.TOP, vm.tab == Tab.REPORT) { vm.go(Tab.REPORT) }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, glyph: Glyph, on: Boolean, onClick: () -> Unit) {
    val c = T.c
    val color = if (on) c.a800 else c.n500
    Column(
        Modifier
            .weight(1f)
            .tap(onClick)
            .padding(top = 11.dp, bottom = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = 1.5.dp.toPx()
            val thick = 5.dp.toPx()
            drawRect(color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w))
            when (glyph) {
                Glyph.LEFT -> drawRect(color, Offset.Zero, Size(thick, size.height))
                Glyph.BOTTOM -> drawRect(color, Offset(0f, size.height - thick), Size(size.width, thick))
                Glyph.TOP -> drawRect(color, Offset.Zero, Size(size.width, thick))
                Glyph.PLAIN -> Unit
            }
        }
        Text(label.uppercase(), style = T.h(10.5.sp, color, 0.08.em), maxLines = 1)
    }
}

@Composable
private fun Onboarding(vm: AppViewModel) {
    val c = T.c
    val l = T.l
    val step = vm.onbStep.coerceIn(0, 2)
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            (0..2).forEach { i ->
                Box(Modifier.width(if (i == step) 26.dp else 8.dp).height(4.dp).background(if (i == step) c.a300 else c.hairline))
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(64.dp)
                .hairline(c.a300)
                .blueprintMarks(c.onAccent.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(Currencies.sym(Currencies.BASE), style = T.h(30.sp, c.a300))
        }
        Spacer(Modifier.height(22.dp))
        Text(l.t("onb.${step + 1}.title"), style = T.h(38.sp, c.onAccent, (-0.01).em, 40.sp))
        Spacer(Modifier.height(16.dp))
        Text(l.t("onb.${step + 1}.body"), style = T.b(17.sp, c.a300, lineHeight = 25.sp))
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (step < 2) l.t("onb.next") else l.t("onb.start"), {
            if (step < 2) vm.onbStep = step + 1 else vm.finishOnboarding()
        })
        Spacer(Modifier.height(10.dp))
        Text(
            if (step < 2) l.t("onb.skip") else l.t("onb.clean"),
            style = T.h(14.sp, c.a300, 0.08.em),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .tap { if (step < 2) vm.onbStep = 2 else vm.startClean() }
                .padding(12.dp),
        )
    }
}
