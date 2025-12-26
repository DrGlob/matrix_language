package org.example.astviewer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var executionView: ASTExecutionView
    private lateinit var viewModel: AstViewerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        executionView = findViewById(R.id.executionView)
        viewModel = ViewModelProvider(this)[AstViewerViewModel::class.java]

        viewModel.graph.observe(this) { nodes ->
            executionView.nodes = nodes
        }
        viewModel.nodeStates.observe(this) { states ->
            executionView.states = states
        }

        assets.open("events.jsonl").use { viewModel.loadGraphAndEvents(it) }
    }
}
