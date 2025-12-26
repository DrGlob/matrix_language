package org.example.astviewer

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var executionView: ASTExecutionView
    private lateinit var startButton: Button
    private lateinit var viewModel: AstViewerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        executionView = findViewById(R.id.astView)
        startButton = findViewById(R.id.startButton)
        viewModel = ViewModelProvider(this)[AstViewerViewModel::class.java]

        viewModel.graph.observe(this) { nodes ->
            executionView.nodes = nodes
        }
        viewModel.nodeStates.observe(this) { states ->
            executionView.states = states
        }

        startButton.setOnClickListener {
            assets.open("events.jsonl").use { viewModel.loadGraphAndEvents(it) }
        }
    }
}
