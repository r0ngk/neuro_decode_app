package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.opencsv.CSVReader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var importButton: Button
    private lateinit var playButton: ImageButton
    private lateinit var chordLabel: TextView
    private lateinit var seekBarTime: TextView
    private lateinit var audioSeekBar: SeekBar
    private lateinit var trackDescriptLabel: TextView

    private lateinit var eegFileUri : Uri
    private var isEEGAvaliable: Boolean = false
    private lateinit var resultChord: Array<String>
    // seekbar parameter and handler
    private var maxSample = 0
    private var currSample = 0
    private var samplePerSec = 2
    private var updateDelay = (1/samplePerSec*1000).toLong()
    private var isPaused = true
    private var isEnded = false
    private var seekBarUpdateHandler = Handler(Looper.getMainLooper())
    private var runnableExist = false
    private lateinit var seekBarUpdateRunnable: Runnable
    // Decoding model
    private lateinit var decoderAgent: Decoder
    private lateinit var utils: Utils


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

            if (!isPaused) {
                togglePlay()
            }
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
                    currSample = progress
                }
                seekBarTime.text = getString(R.string.timeStamp,
                    (progress/samplePerSec/60).toInt(), (progress/samplePerSec%60).toInt());
                // read chord from mem
                chordLabel.text = resultChord[currSample]
            }

            override fun onStartTrackingTouch(seek: SeekBar) {
//                isPaused = true
            }

            override fun onStopTrackingTouch(seek: SeekBar) {
//              isPaused = false
            }
        })

        utils = Utils()
        decoderAgent = Decoder(this, utils)
    }

    private fun togglePlay() {
        isPaused = !isPaused
        if (isPaused) {
            playButton.setBackgroundResource(R.drawable.ic_baseline_play_arrow_24)
        } else {
            playButton.setBackgroundResource(R.drawable.ic_baseline_pause_24)
        }
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
            isEEGAvaliable = false
            maxSample = 0
            if (!isPaused) {
                togglePlay()
            }
            // handle file details
            var cursor = contentResolver.query(eegFileUri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor?.moveToFirst()
            var fileName = nameIndex?.let { cursor?.getString(it) }
            var fileExtension = fileName?.substringAfter(".");
//            println(fileExtension)
            // perform read IO
            val inputStream = contentResolver.openInputStream(eegFileUri)
            val csvReader = CSVReader(InputStreamReader(inputStream))

            // Read csv file line by line
            val rawData = mutableListOf<Array<String>>()
            val header: Array<String> = csvReader.readNext()
            var line: Array<String>? = csvReader.readNext()
            while (line != null) {
                rawData.add(line)
                line = csvReader.readNext()
            }
            csvReader.close()

            // set the seekbar
            currSample = 0
            isEEGAvaliable = true
            val tryResult = decoderAgent.go(header, rawData)
            if (tryResult != null) {
                resultChord = tryResult
                maxSample = resultChord.size
                runOnUiThread {
                    audioSeekBar.progress = 0
                    audioSeekBar.max = resultChord.size-1
                    seekBarTime.text = getString(R.string.timeStamp,0,0)
                    chordLabel.text = resultChord[0]
                    trackDescriptLabel.text = "Now Playing:  "+ fileName?.substringBefore(".")
                }
            }
        }
    }

    private fun updateSeekBar() {
        if (isPaused) {
            return
        }
        if (currSample == maxSample) {
            if (isEnded) {
                currSample = 0
                isEnded = false
                return
            }
            isEnded = true
            togglePlay()
            return
        }
        // update seek bar ui
        audioSeekBar.progress = currSample
        currSample ++
    }

    private fun playDecodedChord(ui: ImageButton) {
        // do nothing if no file if choose
        if (!isEEGAvaliable) {
            Toast.makeText(this@MainActivity,
                "You need to select a EEG file to play",
                Toast.LENGTH_SHORT).show()
            return
        }

        togglePlay()
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