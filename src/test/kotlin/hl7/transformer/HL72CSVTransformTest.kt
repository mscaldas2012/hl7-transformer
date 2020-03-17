package hl7.transformer

import org.junit.jupiter.api.Test

internal class HL72CSVTransformTest {

    @Test
    fun transformSimple() {
        val hl7Transform = HL72CSVTransform.getTransformer("/SimpleTemplate.json")
        val testmsg = javaClass.getResource("/ARLN_GC_DUP.txt").readText()
        val newFile = hl7Transform.transformMessage(testmsg)
        println(newFile)
    }

    @Test
    fun transformInnerObjects() {
        val hl7Transform = HL72CSVTransform.getTransformer("/InnerObjects.json")
        val testmsg = javaClass.getResource("/ARLN_GC_DUP.txt").readText()
        val newFile = hl7Transform.transformMessage(testmsg)
        println(newFile)
    }

    @Test
    fun transformArrayOfArray() {
        val hl7Transform = HL72CSVTransform.getTransformer("/GCTemplate.json")
        val testmsg = javaClass.getResource("/ARLN_GC_DUP.txt").readText()
        val newFile = hl7Transform.transformMessage(testmsg)
        println(newFile)
    }

    @Test
    fun getConfig() {
        val hl7Transform = HL72CSVTransform.getTransformer("/MessageHeaderExtractor.json")
        assert(hl7Transform != null)
        //hl7Transform.config.forEach{ println(it) }
    }

    @Test
    fun transformDHQP() {
        val hl7Transform = HL72CSVTransform.getTransformer("/dhqp_template.json")
        val testmsg = javaClass.getResource("/arln_test_hl7.txt").readText()
        val newFile = hl7Transform.transformMessage(testmsg)
        println(newFile)
    }
}