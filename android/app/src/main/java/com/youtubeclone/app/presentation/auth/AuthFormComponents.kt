package com.youtubeclone.app.presentation.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.theme.redTextButtonColors
import com.youtubeclone.app.presentation.common.AppLogo
import com.youtubeclone.app.presentation.common.BackTopBar
import com.youtubeclone.app.presentation.common.UiError
import com.youtubeclone.app.presentation.common.asText

// Éléments communs aux écrans Connexion et Inscription, d'après les captures connexion-03 et -07 (iOS) :
// logo centré, titre court, champs à libellé flottant avec l'erreur dessous, bouton blanc pleine
// largeur, puis les liens discrets en bas de l'écran.

/**
 * Page de formulaire : [fields] et le bouton en haut, [footer] (liens) collé en bas de l'écran.
 * La page défile quand le clavier ou une petite hauteur d'écran ne laissent pas assez de place.
 */
@Composable
internal fun AuthLayout(
    @StringRes title: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        BackTopBar(title = stringResource(title), onBack = onBack)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    AppLogo(height = 40.dp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
                    fields()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = footer,
                )
            }
        }
    }
}

/** Champ à libellé flottant ; l'erreur (validation ou réponse du serveur) s'affiche dessous. */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: UiError?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isPassword: Boolean = false,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = (error?.asText() ?: supportingText)?.let { text -> { Text(text) } },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
            focusedLabelColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = MaterialTheme.colorScheme.onBackground,
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Bouton principal blanc sur fond sombre ; un indicateur remplace le texte pendant l'appel. */
@Composable
internal fun SubmitButton(
    @StringRes text: Int,
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    val container = MaterialTheme.colorScheme.onBackground
    val content = MaterialTheme.colorScheme.background
    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        shape = CircleShape,
        // Pendant l'appel, le bouton garde ses couleurs : pas de passage au gris.
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(stringResource(text), fontWeight = FontWeight.Bold)
        }
    }
}

/** Liens du bas : basculer entre connexion et inscription, ou entrer en visiteur. */
@Composable
internal fun AuthFooterLinks(
    @StringRes switchLabel: Int,
    onSwitchClick: () -> Unit,
    onContinueAsVisitor: () -> Unit,
) {
    TextButton(colors = redTextButtonColors(), onClick = onSwitchClick) {
        Text(
            text = stringResource(switchLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(colors = redTextButtonColors(), onClick = onContinueAsVisitor) {
        Text(
            text = stringResource(R.string.auth_continue_as_visitor),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
