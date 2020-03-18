package hl7.transformer

import com.google.gson.*
import open.HL7PET.tools.HL7ParseUtils
import java.lang.UnsupportedOperationException

class HL72CSVTransform(val template: JsonObject) {

    companion object {
        //Factory Method
        fun getTransformer(configFile: String):HL72CSVTransform {
            val configFile = HL72CSVTransform::class.java.getResource(configFile).readText()
            val parser = JsonParser()
            val jsonbody = parser.parse(configFile).asJsonObject

            return HL72CSVTransform(jsonbody)
        }
    }
    fun transformMessage(hl7Message: String): String {
        val hl7Parser = HL7ParseUtils(hl7Message, null)
        val copyDoc = template.deepCopy()
        navigateJson(hl7Parser, template, copyDoc = copyDoc)
        return copyDoc.toString()
    }



    private fun createJsonTree(bline: List<String>, fromAttr: String, leaf: JsonArray):JsonElement  {
        val retJson = JsonObject()
        if (bline.size == 1)
            retJson.add(bline[0], leaf)
        else if (bline[0] != fromAttr)
            return createJsonTree(bline.drop(1), fromAttr, leaf)
        else
            retJson.add(bline[0], createJsonTree(bline.drop(1), bline[1], leaf))
        return retJson
    }

    private fun createJsonTree(bline: List<String>, fromAttr: String,  leafNode: String): JsonElement {
        val retJson = JsonObject()
        if (bline.size == 1)
            retJson.addProperty(bline[0], leafNode)
         else if (bline[0] != fromAttr)
            return createJsonTree(bline.drop(1), fromAttr, leafNode)
         else
            retJson.add(bline[0], createJsonTree(bline.drop(1), bline[1], leafNode))
        return retJson
    }

    private fun mergeNodes(targetNode: JsonObject,  newNode: JsonObject) {
        var targetPointer = targetNode
        var newNodePointer = newNode
        while( targetPointer.has(newNodePointer.keySet().first())) {
            targetPointer = targetPointer.get(newNodePointer.keySet().first()).asJsonObject
            newNodePointer = newNodePointer.get(newNodePointer.keySet().first()).asJsonObject
        }
        targetPointer.add(newNodePointer.keySet().first(), newNodePointer.get(newNodePointer.keySet().first()))
    }

    private fun navigateJson(hl7Message: HL7ParseUtils, elem: JsonElement, parent: JsonElement? = null, path: String = "", attr: String = "", copyDoc: JsonElement, copyDocParent: JsonElement? =null) {
        if (elem.isJsonObject) {
            (elem as JsonObject).entrySet().map { prop -> navigateJson(hl7Message, prop.value, elem, "$path.${prop.key}", prop.key, (copyDoc as JsonObject).get(prop.key), copyDoc) }
        } else if (elem.isJsonArray) {
            val map = mutableMapOf<String, Array<out Array<String>>?>() //Array<out Array<String>>?>
            processArray(elem as JsonArray, map, hl7Message, path)
            val newArray = createNewJsonArray(map, attr)
            if (copyDocParent is JsonObject) {
                copyDocParent.remove(attr)
                copyDocParent.add(attr, newArray)
            }

        } else if (elem.isJsonPrimitive) {
            val prim = elem.asJsonPrimitive.asString
            val newValue:Any? = transformVariable(hl7Message, prim)
//            if (newValue is String) {
//                if (newValue != null && !newValue.equals(prim))
//                    if (copyDocParent!!.isJsonObject)
//                        (copyDocParent as JsonObject).addProperty(attr, newValue)
//                    else if (parent!!.isJsonArray)
//                        (copyDocParent as JsonArray)[attr.toInt()] = JsonPrimitive(newValue)
//            }
            if (newValue is Array<*>) {
                newValue.forEachIndexed { i, it ->
                    (it as Array<*>).forEachIndexed { ii, it2 ->
                        if (it2 != null && !it2.equals(prim))
                            if (parent!!.isJsonObject)
                                (copyDocParent as JsonObject).addProperty(attr , it2 as String)
                            else if (parent.isJsonArray)
                                (copyDocParent as JsonArray)[i] = JsonPrimitive(it2 as String)
                    }
                }
            }

        }
    }

    private fun processArray(elem: JsonArray, mapValues: MutableMap<String, Array<out Array<String>>?>, hl7Message: HL7ParseUtils, path: String) {
        elem.forEachIndexed { idx, prop ->
            populateArrayProperties(prop, mapValues, hl7Message, path)
        }
    }

    private fun populateArrayProperties(prop: JsonElement, map: MutableMap<String, Array<out Array<String>>?>, hl7Message: HL7ParseUtils, path: String) {
        if (prop.isJsonObject) {
            (prop as JsonObject).entrySet().map{ pp ->
                if (pp.value.isJsonPrimitive) {
                        transformVariable(hl7Message, pp.value.asString)?.let { map.put("$path.${pp.key}", it) }
                } else if (pp.value.isJsonObject) {
                    populateArrayProperties(pp.value, map, hl7Message, "$path.${pp.key}")
                } else if (pp.value.isJsonArray) {
                   throw UnsupportedOperationException("Unable to parse Arrays of arrays")
                } else {
                    println("Not sure: ${pp.value.javaClass}")
                }

            }
        } else {
            println("prop is ${prop.javaClass}")
        }
    }

    private fun createNewJsonArray(mapValues: MutableMap<String, Array<out Array<String>>?>, attr: String): JsonArray {
        //Add all Elements to array:
        val newArray = JsonArray()
        for (index in 1..(mapValues.values.first()?.size ?: 0)) {
            val newObj = JsonObject()
            mapValues.forEach { k ->
                val kSize = k.value?.size ?: 0
                val innerValue = if (kSize >= index) k.value?.get(index - 1) else null
                if (innerValue == null)
                    newObj.add(k.key, null)
                else when (innerValue.size) {
                    0 -> newObj.add(k.key.substring(1).split('.').last(), null)
                    1 -> {
                        val bline = k.key.split('.')
                        val tempJsonObj = createJsonTree(bline, attr, innerValue[0])
                        mergeNodes(newObj, tempJsonObj as JsonObject)
                    }
                    else -> {
                        val newInnerArray = JsonArray()
                        innerValue.forEach { r -> newInnerArray.add(r) }
                        val bline = k.key.split('.')
                        val arrayWrapper = JsonObject()
                        arrayWrapper.add(attr, newInnerArray)
                        val tempJsonObj = createJsonTree(bline, attr, newInnerArray)
                        mergeNodes(newObj, tempJsonObj as JsonObject)
//                        newObj.add(k.key, newArray)
                    }
                }

            }
            newArray.add(newObj.get(attr))

        }
        return newArray

    }


    private fun transformVariable(hl7Parser: HL7ParseUtils,  prim: String?): Array<out Array<String>>? {
            val optionalValue = hl7Parser.getValue(prim)
            if (optionalValue.isDefined)
                return optionalValue.get()
            return null
    }
}

