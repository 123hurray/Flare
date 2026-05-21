package dev.dimension.flare.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import compose.icons.fontawesomeicons.BrandsGroup
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public val BrandsGroup.Jike: ImageVector
    get() {
        if (_jike != null) {
            return _jike!!
        }
        _jike =
            ImageVector
                .Builder(
                    name = "Jike",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                    addPath(
                        pathData =
                            PathParser()
                                .parsePathString(
                                    "M14.25 4.5C15.49 4.5 16.5 5.51 16.5 6.75V15.2C16.5 18.29 14.19 20.5 10.93 20.5C8.57 20.5 6.82 19.38 5.82 17.74C5.18 16.69 5.59 15.31 6.69 14.77C7.68 14.28 8.86 14.64 9.45 15.57C9.82 16.15 10.25 16.45 10.9 16.45C11.69 16.45 12 15.95 12 15.13V6.75C12 5.51 13.01 4.5 14.25 4.5Z",
                                ).toNodes(),
                        fill = SolidColor(Color.Black),
                    )
                }.build()
        return _jike!!
    }

private var _jike: ImageVector? = null
