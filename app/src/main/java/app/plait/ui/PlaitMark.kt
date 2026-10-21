package app.plait.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.plait.R

@Composable
fun PlaitMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_plait_mark), null, modifier.size(size))
}
