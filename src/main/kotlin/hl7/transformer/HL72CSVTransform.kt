package hl7.transformer

import com.google.gson.*
import open.HL7PET.tools.HL7ParseUtils

class HL72CSVTransform(val template: JsonObject) {

    companion object {
        //Factory Method
        fun getTransformer(configFile: String):HL72CSVTransform {
            val configFile = javaClass.getResource(configFile).readText()
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
            processArray(elem as JsonArray, hl7Message, path, attr, copyDocParent)
        } else if (elem.isJsonPrimitive) {
            val prim = elem.asJsonPrimitive.asString
            val newValue:Any? = transformVariable(hl7Message, path.substring(1), prim)
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

    private fun processArray(elem: JsonArray, hl7Message: HL7ParseUtils, path: String, attr: String, parentElem: JsonElement) {
//        val combinedArray = JsonArray()
        elem.forEachIndexed { idx, prop ->
            val mapValues = mutableMapOf<String, Array<out Array<String>>?>()
            populateArrayProperties(prop, mapValues, hl7Message, path, parentElem)
            val newArray = createNewJsonArray(mapValues, attr)
//            if (parentElem is JsonArray)
//                parentElem.addAll(newArray)
//            else if ((parentElem as JsonObject).get(attr) is JsonArray) {
//                (parentElem.get(attr) as JsonArray).addAll(newArray)
////                combinedArray.addAll(newArray)
//            }
         //   ((parentElem.get(attr) as JsonArray) as JsonObject).add()
        }


//        if (parentElem is JsonObject) {
//            parentElem.remove(attr)
//            parentElem.add(attr, combinedArray)
////        } else if (parentElem is JsonArray) {
////            parentElem.add(newArray)
//        }
    }

    private fun populateArrayProperties(prop: JsonElement, map: MutableMap<String, Array<out Array<String>>?>, hl7Message: HL7ParseUtils, path: String, parentElement: JsonElement) {
        if (prop.isJsonObject) {
            (prop as JsonObject).entrySet().mapIndexed { i, pp ->
                if (pp.value.isJsonPrimitive)
                    transformVariable(hl7Message, path.substring(1), pp.value.asString)?.let { map.put("$path.${pp.key}", it) }
                else if (pp.value.isJsonObject) {
                    populateArrayProperties(pp.value, map, hl7Message, "$path.${pp.key}", parentElement)
                } else if (pp.value.isJsonArray){
                    println("found array")
                    processArray(pp.value as JsonArray, hl7Message, "$path.${pp.key}", pp.key, prop)
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
    //                            }
                    else -> {
                        val newInnerArray = JsonArray()
                        innerValue?.forEach { r -> newInnerArray.add(r) }
                        newObj.add(k.key, newArray)
                    }
                }

            }
            newArray.add(newObj.get(attr))

        }
        return newArray
    }

//    private fun navigateArray( hl7Message: HL7ParseUtils, jsonArray: JsonArray, ) {
//
//    }

    private fun transformVariable(hl7Parser: HL7ParseUtils, substring: String, prim: String?): Array<out Array<String>>? {
            val optionalValue = hl7Parser.getValue(prim, false)
            if (optionalValue.isDefined)
                return optionalValue.get()
            return null
    }
}

