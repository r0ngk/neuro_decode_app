package com.example.myapplication

import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor

class Decoder(activity: MainActivity, utils: Utils) {

    private var module: Module
    private var modelName = "model2.pt"
    private val resultMap = arrayOf("A", "Am", "Bm", "C", "D", "Dm", "E", "Em", "F", "G")
    private val tensorRowName = mutableListOf<String>(
        "Delta", "Theta", "Alpha1", "Alpha2", "Beta1", "Beta2", "Gamma1", "Gamma2"
    )

    init {
        module = LiteModuleLoader.load(
            utils.assetFilePath(activity, modelName))
    }

    private fun getTensorIdx(header: Array<String>): Pair<MutableList<Int>, Boolean> {

        var tensorRowIdx = mutableListOf<Int>()
        var succ = true
        for (name in tensorRowName) {
            val idx = header.indexOf(name)
            if (idx != -1) {
                tensorRowIdx.add(idx)
            } else {
                succ = false
                break
                // TODO: remind user for error
            }
        }
        return Pair(tensorRowIdx, succ)
    }

    private fun inputTensorGenerator(tensorRowIdx: MutableList<Int>, rawData: MutableList<Array<String>>): Tensor {
        val columnSize = tensorRowIdx.size
        val tensorShape = LongArray(2)
        tensorShape[0] = rawData.size.toLong()
        tensorShape[1] = columnSize.toLong()

        val arr = FloatArray(rawData.size * columnSize)
        for (i in 0 until rawData.size) {
            for (j in 0 until columnSize) {
                arr[i*columnSize + j] = rawData[i][tensorRowIdx[j]].toFloat()
            }
        }
        // Create the tensor and return it
        return Tensor.fromBlob(arr, tensorShape)
    }

    private fun mapModelResult(output: LongArray): Array<String> {

        var resultChord = Array<String>(output.size) {""}
        for (i in output.indices) {
            resultChord[i] = resultMap[output[i].toInt()]
        }
        return resultChord
    }

    public fun go(header: Array<String>, rawData: MutableList<Array<String>>): Array<String>?
    {
        val (tensorRowIdx, success) = getTensorIdx(header)
        if (!success) {
            return null
        }
        val inputTensor = inputTensorGenerator(tensorRowIdx, rawData)
        val output = module.forward(IValue.from(inputTensor)).toTensor().dataAsLongArray
        return mapModelResult(output)
    }

}