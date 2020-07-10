package com.github.ckaag.propertykeychecker

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.net.URL
import java.nio.charset.Charset

//three way merge for language file (primary intended usage was migrating liferay language hooks to new versions)

fun main(args: Array<String>) = PropertyKeyChecker().main(args)

class PropertyKeyChecker : CliktCommand() {
    val propertyFile: File by option(
        help = "The previously written property file with message keys you want to compare with the new underlying changes",
        names = *arrayOf("-i")
    ).file(canBeFile = true, mustBeReadable = true, mustExist = true)
        .default(File("./my_test.properties")) /*todo make required instead*/
    val oldPropertyFileHttpUrl: String by option(help = "The link to the old overwritten file, e.g. 'https://raw.githubusercontent.com/liferay/liferay-portal/6.2/portal-impl/src/content/Language.properties'",
        names = *arrayOf("-p")
    ).default(
        "https://raw.githubusercontent.com/liferay/liferay-portal/6.2/portal-impl/src/content/Language.properties"
    ) /*todo make required instead*/
    val newPropertyFileHttpUrl: String by option(
        help = "The link to the old overwritten file, e.g. 'https://raw.githubusercontent.com/liferay/liferay-portal/7.2.x/portal-impl/src/content/Language_de.properties'",
        names = *arrayOf("-n")
    ).default("https://raw.githubusercontent.com/liferay/liferay-portal/7.2.x/portal-impl/src/content/Language_de.properties") /*todo make required instead*/

    val showChangedValues: Boolean by option().flag(default = false)

    override fun run() {
        printChangedKeys()
    }

    private fun printChangedKeys() {
        val yourKV = readKeysFromFile(propertyFile).asMap()
        val oldSpecKV = readSpecKeysFromUrl(URL(oldPropertyFileHttpUrl)).asMap()
        val newSpecKV = readSpecKeysFromUrl(URL(newPropertyFileHttpUrl)).asMap()

        val changes = yourKV.findInBoth(oldSpecKV, newSpecKV).sortedBy { it.sortableKey() }

        //print out every change
        changes.forEach { change ->
            change.toPrintableString(showChangedValues)?.also { println(it) }
        }
    }

}


fun readKeysFromFile(file: File): List<KeyValue> = file.readLines(Charsets.UTF_8).mapNotNull { it.parseAsKeyValue() }

fun readSpecKeysFromUrl(url: URL): List<KeyValue> =
    url.readUrlToLines(Charsets.UTF_8).mapNotNull { it.parseAsKeyValue() }

fun URL.readUrlToLines(charset: Charset): List<String> = this.readText(charset).lines()

fun String.parseAsKeyValue(): KeyValue? {
    val strBeforeComment = this.substringBefore('#')
    if (!strBeforeComment.isBlank()) {
        val idx = strBeforeComment.indexOf('=')
        if (idx > 0) {
            val key = strBeforeComment.substring(0, idx).trim()
            val value = strBeforeComment.substring(idx + 1).trim()
            if (!key.isBlank()) {
                return KeyValue(Key(key.trim()), Value(value.trim()))
            }
        }
    }
    return null
}

fun List<KeyValue>.asMap(): Map<Key, Value> = this.map { it.key to it.value }.toMap()

fun Map<Key, Value>.findInBoth(oldSpec: Map<Key, Value>, newSpec: Map<Key, Value>): List<KeyChange> {
    val result = mutableListOf<KeyChange?>()
    this.forEach { (overKey, overValue) ->
        val oldValueForSameKey = oldSpec[overKey]
        if (oldValueForSameKey != null) {
            val newValueForSameKey = newSpec[overKey]
            if (newValueForSameKey != null) {
                val hasSameValue = newValueForSameKey == oldValueForSameKey
                result.add(
                    when {
                        newValueForSameKey == overValue -> {
                            KeyChange.ValueNowDefault(overKey, overValue)
                        }
                        hasSameValue -> {
                            null /* nothing changed */
                        }
                        else -> {
                            KeyChange.ValueChanged(overKey, overValue, oldValueForSameKey, newValueForSameKey)
                        }
                    }
                )
            } else {
                val alternatives =
                    newSpec.filter { it.value == overValue && !oldSpec.containsKey(it.key) }.toList().firstOrNull()
                result.add(
                    if (alternatives != null) {
                        KeyChange.KeyDoesNotExistAnymoreButReplacement(overKey, alternatives.first, alternatives.second)
                    } else {
                        KeyChange.KeyDoesNotExistAnymore(overKey)
                    }
                )
            }
        } else {
            // else it wasn't an overwrite in the first place, but check for new complications
            val newValue = newSpec[overKey]
            result.add(
                if (newValue == overValue) {
                    KeyChange.NewKeyThatDuplicatesAndIsIdentical(overKey, overValue)
                } else if (newValue != null) {
                    KeyChange.NewKeyThatDuplicates(overKey, overValue, newValue)
                } else {
                    null
                }
            )
        }
        newSpec.filter { it.value == overValue && !oldSpec.containsKey(it.key) && it.key != overKey }.forEach {
            result.add(KeyChange.NewKeyWithSameValue(overKey, it.key, it.value))
        }
    }
    return result.filterNotNull().toList()
}

data class KeyValue(val key: Key, val value: Value)
data class Key(val str: String)
data class Value(val str: String)

sealed class KeyChange(private val key: Key) {
    data class ValueChanged(
        val k: Key,
        val overwriteValue: Value,
        val oldOverwrittenValue: Value,
        val newOverwrittenValue: Value
    ) : KeyChange(k) {
        override fun toPrintableString(showChangedValues: Boolean): String? = if (showChangedValues) {
            ("Default Value has changed for key '${this.k.str}' : '${this.oldOverwrittenValue.str}' -> '${this.newOverwrittenValue.str}' | replaced by your '${this.overwriteValue.str}'")
        } else null
    }

    data class ValueNowDefault(val k: Key, val v: Value) : KeyChange(k) {
        override fun toPrintableString(showChangedValues: Boolean): String? =
            ("The value you have used before is now default: key '${this.k.str}' : value '${this.v.str}'")
    }

    data class KeyDoesNotExistAnymore(val k: Key) : KeyChange(k) {
        override fun toPrintableString(showChangedValues: Boolean): String? =
            ("The following key has vanished: key '${this.k.str}'")
    }
    
    data class KeyDoesNotExistAnymoreButReplacement(
        val missingKey: Key,
        val newKeyWithSameValue: Key,
        val value: Value
    ) : KeyChange(missingKey) {
        override fun toPrintableString(showChangedValues: Boolean): String? =
            ("The following key has vanished: key '${this.missingKey.str}' but there is a new key '${newKeyWithSameValue.str}' with the same value '${this.value.str}'")
    }

    data class NewKeyThatDuplicates(val k: Key, val overwriteValue: Value, val newOverwrittenValue: Value) :
        KeyChange(k) {
        override fun toPrintableString(showChangedValues: Boolean): String? =
            ("I have found a new key that is the same as one of yours: key '${this.k.str}' : your value is '${this.overwriteValue.str}', the new key's value (which will be overwritten) is '${this.newOverwrittenValue.str}'")
    }

    data class NewKeyThatDuplicatesAndIsIdentical(val k: Key, val overwriteValue: Value) :
        KeyChange(k) {
        override fun toPrintableString(showChangedValues: Boolean): String? =
            ("I have found a new key that is the same key/value as one of yours with the same value: key '${this.k.str}' : the value is '${this.overwriteValue.str}'")
    }

    data class NewKeyWithSameValue(val oldKey: Key, val newKey: Key, val value: Value) : KeyChange(oldKey) {
        override fun toPrintableString(showChangedValues: Boolean): String? =
            ("I have found a new Key that has the same value as one of your's: your key '${oldKey.str}' and the new key is '${newKey.str}' with the value in question being '${value.str}'")
    }

    abstract fun toPrintableString(showChangedValues: Boolean): String?

    fun sortableKey(): String = "${this.javaClass.simpleName}_${this.key.str}"
}
