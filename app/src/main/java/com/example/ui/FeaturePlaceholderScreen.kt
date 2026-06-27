package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturePlaceholderScreen(
    featureName: String,
    emoji: String,
    language: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = featureName,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B6B2F)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color(0xFF1B6B2F)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFFAFAF7) // prescribed warm white
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Large visual card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2F3E7), RoundedCornerShape(24.dp))
                    .testTag("placeholder_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = emoji,
                        fontSize = 72.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = featureName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E271E),
                        textAlign = TextAlign.Center
                    )

                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Building",
                        tint = Color(0xFF1B6B2F),
                        modifier = Modifier.size(36.dp)
                    )

                    Text(
                        text = when (language) {
                            "mr" -> "लवकरच येत आहे — पुढील बांधणीत समाविष्ट"
                            "hi" -> "जल्द ही आ रहा है — अगली कड़ी में शामिल"
                            else -> "Coming Soon — Building Next"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF555F55),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = when (language) {
                            "mr" -> "आपण व्हॉईस असिस्टंट वापरून या सुविधेविषयी माहिती विचारू शकता!"
                            "hi" -> "आप वॉयस असिस्टेंट का उपयोग करके इस सुविधा के बारे में पूछ सकते हैं!"
                            else -> "You can ask questions about this feature using the Voice Assistant!"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF889588),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
