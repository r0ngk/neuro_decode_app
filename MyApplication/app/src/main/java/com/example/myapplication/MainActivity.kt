package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opencsv.CSVReader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.lang3.ObjectUtils.Null
import java.io.File
import java.io.InputStreamReader
import kotlin.math.floor


class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var importButton: Button
    private lateinit var playButton: Button
    private lateinit var chordLabel: TextView
    private lateinit var seekBarTime: TextView
    private lateinit var audioSeekBar: SeekBar
    private lateinit var trackDescriptLabel: TextView

    private lateinit var eegFileUri : Uri
    private lateinit var eegHeader: Array<String>
    private var isEEGAvaliable: Boolean = false
    private var chordList = mutableListOf<String>()
    // seekbar parameter and handler
    private var maxSample = 0
    private var currSample = 0
    private var samplePerSec = 2
    private var updateDelay = (1/samplePerSec*1000).toLong()
    private var isPaused = true
    private var seekBarUpdateHandler = Handler(Looper.getMainLooper())
    private var runnableExist = false
    private lateinit var seekBarUpdateRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        importButton = findViewById(R.id.importButton)
        playButton = findViewById(R.id.playButton)
        chordLabel = findViewById(R.id.chordNameLabel)
        audioSeekBar = findViewById(R.id.seekBar)
        audioSeekBar.max = 0
        seekBarTime = findViewById(R.id.barTimeLabel)
        trackDescriptLabel = findViewById(R.id.trackDescriptLabel)

        importButton.setOnClickListener {

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT) //SAF framework
            intent.type = "*/*"
            getContent.launch(intent)

        }

        playButton.setOnClickListener {
            playDecodedChord(playButton)
        }

        audioSeekBar?.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seek: SeekBar,
                                           progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekBarTime.text = getString(R.string.timeStamp,
                        (progress/samplePerSec/60).toInt(), (progress/samplePerSec%60).toInt());
                }
                currSample = progress
            }

            override fun onStartTrackingTouch(seek: SeekBar) {
                isPaused = true
            }

            override fun onStopTrackingTouch(seek: SeekBar) {
                isPaused = false
            }
        })

    }

    private var getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // try to get the input stream of the csv file
            eegFileUri = result.data?.data!!
            readEEGData()
        }
    }

    private fun readEEGData() {

        GlobalScope.launch {
            // Preparation
            chordList.clear()
            isEEGAvaliable = false
            maxSample = 0
            if (!isPaused) {
                isPaused = true
                playButton.text = "play"
            }
            // handle file details
            var cursor = contentResolver.query(eegFileUri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor?.moveToFirst()
            var fileName = nameIndex?.let { cursor?.getString(it) }
            var fileExtension = fileName?.substringAfter(".");
            println(fileExtension)
            // perform read IO
            val inputStream = contentResolver.openInputStream(eegFileUri)
            val csvReader = CSVReader(InputStreamReader(inputStream))

            // Read csv file line by line
            eegHeader = csvReader.readNext()
            var line: Array<String>? = csvReader.readNext()
            while (line != null) {
                chordList.add(line[4]) // extract line[4] for the "raw" column
                line = csvReader.readNext()
                maxSample ++
            }
            csvReader.close()

            // set the seekbar
            currSample = 0
            runOnUiThread {
                audioSeekBar.progress = 0
                audioSeekBar.max = maxSample
                seekBarTime.text = getString(R.string.timeStamp,0,0)
                chordLabel.text = chordList[0]
                trackDescriptLabel.text = fileName?.substringBefore(".")
            }
            isEEGAvaliable = true
        }
    }


    private fun updateSeekBar() {
        if (currSample >= maxSample || isPaused) {
            return
        }
        // read chord from mem
        chordLabel.text = chordList[currSample]
        // update seek bar ui
        audioSeekBar.progress = currSample
        seekBarTime.text = getString(R.string.timeStamp,
                                    (currSample/samplePerSec/60).toInt(), (currSample/samplePerSec%60).toInt());

        currSample ++
    }

    private fun playDecodedChord(ui: Button) {
        // do nothing if no file if choose
        if (!isEEGAvaliable) {
            Toast.makeText(this@MainActivity,
                "You need to select a EEG file to play",
                Toast.LENGTH_SHORT).show()
            return
        }
        // toggle UI
        if (isPaused) {
            ui.text = "pause" //ori UI is "play"
        } else {
            ui.text = "play"
        }
        isPaused = !isPaused
        // set up handler to update the seekbar
        if (runnableExist) {
            return
        }
        runnableExist = true
        seekBarUpdateRunnable = Runnable {
            updateSeekBar() // some action(s)
            seekBarUpdateHandler.postDelayed(seekBarUpdateRunnable, 500)
        }
        seekBarUpdateHandler.postDelayed(seekBarUpdateRunnable, 500)
    }
}