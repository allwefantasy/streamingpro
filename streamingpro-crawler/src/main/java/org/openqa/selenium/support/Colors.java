/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.support;

// Basic colour keywords as defined by the W3C HTML4 spec
// See http://www.w3.org/TR/css3-color/#html4

public enum Colors {
    TRANSPARENT(new Color(0, 0, 0, 0d)),
    ALICEBLUE(new Color(240, 248, 255, 1d)),
    ANTIQUEWHITE(new Color(250, 235, 215, 1d)),
    AQUA(new Color(0, 255, 255, 1d)),
    AQUAMARINE(new Color(127, 255, 212, 1d)),
    AZURE(new Color(240, 255, 255, 1d)),
    BEIGE(new Color(245, 245, 220, 1d)),
    BISQUE(new Color(255, 228, 196, 1d)),
    BLACK(new Color(0, 0, 0, 1d)),
    BLANCHEDALMOND(new Color(255, 235, 205, 1d)),
    BLUE(new Color(0, 0, 255, 1d)),
    BLUEVIOLET(new Color(138, 43, 226, 1d)),
    BROWN(new Color(165, 42, 42, 1d)),
    BURLYWOOD(new Color(222, 184, 135, 1d)),
    CADETBLUE(new Color(95, 158, 160, 1d)),
    CHARTREUSE(new Color(127, 255, 0, 1d)),
    CHOCOLATE(new Color(210, 105, 30, 1d)),
    CORAL(new Color(255, 127, 80, 1d)),
    CORNFLOWERBLUE(new Color(100, 149, 237, 1d)),
    CORNSILK(new Color(255, 248, 220, 1d)),
    CRIMSON(new Color(220, 20, 60, 1d)),
    CYAN(new Color(0, 255, 255, 1d)),
    DARKBLUE(new Color(0, 0, 139, 1d)),
    DARKCYAN(new Color(0, 139, 139, 1d)),
    DARKGOLDENROD(new Color(184, 134, 11, 1d)),
    DARKGRAY(new Color(169, 169, 169, 1d)),
    DARKGREEN(new Color(0, 100, 0, 1d)),
    DARKGREY(new Color(169, 169, 169, 1d)),
    DARKKHAKI(new Color(189, 183, 107, 1d)),
    DARKMAGENTA(new Color(139, 0, 139, 1d)),
    DARKOLIVEGREEN(new Color(85, 107, 47, 1d)),
    DARKORANGE(new Color(255, 140, 0, 1d)),
    DARKORCHID(new Color(153, 50, 204, 1d)),
    DARKRED(new Color(139, 0, 0, 1d)),
    DARKSALMON(new Color(233, 150, 122, 1d)),
    DARKSEAGREEN(new Color(143, 188, 143, 1d)),
    DARKSLATEBLUE(new Color(72, 61, 139, 1d)),
    DARKSLATEGRAY(new Color(47, 79, 79, 1d)),
    DARKSLATEGREY(new Color(47, 79, 79, 1d)),
    DARKTURQUOISE(new Color(0, 206, 209, 1d)),
    DARKVIOLET(new Color(148, 0, 211, 1d)),
    DEEPPINK(new Color(255, 20, 147, 1d)),
    DEEPSKYBLUE(new Color(0, 191, 255, 1d)),
    DIMGRAY(new Color(105, 105, 105, 1d)),
    DIMGREY(new Color(105, 105, 105, 1d)),
    DODGERBLUE(new Color(30, 144, 255, 1d)),
    FIREBRICK(new Color(178, 34, 34, 1d)),
    FLORALWHITE(new Color(255, 250, 240, 1d)),
    FORESTGREEN(new Color(34, 139, 34, 1d)),
    FUCHSIA(new Color(255, 0, 255, 1d)),
    GAINSBORO(new Color(220, 220, 220, 1d)),
    GHOSTWHITE(new Color(248, 248, 255, 1d)),
    GOLD(new Color(255, 215, 0, 1d)),
    GOLDENROD(new Color(218, 165, 32, 1d)),
    GRAY(new Color(128, 128, 128, 1d)),
    GREY(new Color(128, 128, 128, 1d)),
    GREEN(new Color(0, 128, 0, 1d)),
    GREENYELLOW(new Color(173, 255, 47, 1d)),
    HONEYDEW(new Color(240, 255, 240, 1d)),
    HOTPINK(new Color(255, 105, 180, 1d)),
    INDIANRED(new Color(205, 92, 92, 1d)),
    INDIGO(new Color(75, 0, 130, 1d)),
    IVORY(new Color(255, 255, 240, 1d)),
    KHAKI(new Color(240, 230, 140, 1d)),
    LAVENDER(new Color(230, 230, 250, 1d)),
    LAVENDERBLUSH(new Color(255, 240, 245, 1d)),
    LAWNGREEN(new Color(124, 252, 0, 1d)),
    LEMONCHIFFON(new Color(255, 250, 205, 1d)),
    LIGHTBLUE(new Color(173, 216, 230, 1d)),
    LIGHTCORAL(new Color(240, 128, 128, 1d)),
    LIGHTCYAN(new Color(224, 255, 255, 1d)),
    LIGHTGOLDENRODYELLOW(new Color(250, 250, 210, 1d)),
    LIGHTGRAY(new Color(211, 211, 211, 1d)),
    LIGHTGREEN(new Color(144, 238, 144, 1d)),
    LIGHTGREY(new Color(211, 211, 211, 1d)),
    LIGHTPINK(new Color(255, 182, 193, 1d)),
    LIGHTSALMON(new Color(255, 160, 122, 1d)),
    LIGHTSEAGREEN(new Color(32, 178, 170, 1d)),
    LIGHTSKYBLUE(new Color(135, 206, 250, 1d)),
    LIGHTSLATEGRAY(new Color(119, 136, 153, 1d)),
    LIGHTSLATEGREY(new Color(119, 136, 153, 1d)),
    LIGHTSTEELBLUE(new Color(176, 196, 222, 1d)),
    LIGHTYELLOW(new Color(255, 255, 224, 1d)),
    LIME(new Color(0, 255, 0, 1d)),
    LIMEGREEN(new Color(50, 205, 50, 1d)),
    LINEN(new Color(250, 240, 230, 1d)),
    MAGENTA(new Color(255, 0, 255, 1d)),
    MAROON(new Color(128, 0, 0, 1d)),
    MEDIUMAQUAMARINE(new Color(102, 205, 170, 1d)),
    MEDIUMBLUE(new Color(0, 0, 205, 1d)),
    MEDIUMORCHID(new Color(186, 85, 211, 1d)),
    MEDIUMPURPLE(new Color(147, 112, 219, 1d)),
    MEDIUMSEAGREEN(new Color(60, 179, 113, 1d)),
    MEDIUMSLATEBLUE(new Color(123, 104, 238, 1d)),
    MEDIUMSPRINGGREEN(new Color(0, 250, 154, 1d)),
    MEDIUMTURQUOISE(new Color(72, 209, 204, 1d)),
    MEDIUMVIOLETRED(new Color(199, 21, 133, 1d)),
    MIDNIGHTBLUE(new Color(25, 25, 112, 1d)),
    MINTCREAM(new Color(245, 255, 250, 1d)),
    MISTYROSE(new Color(255, 228, 225, 1d)),
    MOCCASIN(new Color(255, 228, 181, 1d)),
    NAVAJOWHITE(new Color(255, 222, 173, 1d)),
    NAVY(new Color(0, 0, 128, 1d)),
    OLDLACE(new Color(253, 245, 230, 1d)),
    OLIVE(new Color(128, 128, 0, 1d)),
    OLIVEDRAB(new Color(107, 142, 35, 1d)),
    ORANGE(new Color(255, 165, 0, 1d)),
    ORANGERED(new Color(255, 69, 0, 1d)),
    ORCHID(new Color(218, 112, 214, 1d)),
    PALEGOLDENROD(new Color(238, 232, 170, 1d)),
    PALEGREEN(new Color(152, 251, 152, 1d)),
    PALETURQUOISE(new Color(175, 238, 238, 1d)),
    PALEVIOLETRED(new Color(219, 112, 147, 1d)),
    PAPAYAWHIP(new Color(255, 239, 213, 1d)),
    PEACHPUFF(new Color(255, 218, 185, 1d)),
    PERU(new Color(205, 133, 63, 1d)),
    PINK(new Color(255, 192, 203, 1d)),
    PLUM(new Color(221, 160, 221, 1d)),
    POWDERBLUE(new Color(176, 224, 230, 1d)),
    PURPLE(new Color(128, 0, 128, 1d)),
    REBECCAPURPLE(new Color(102, 51, 153, 1d)),
    RED(new Color(255, 0, 0, 1d)),
    ROSYBROWN(new Color(188, 143, 143, 1d)),
    ROYALBLUE(new Color(65, 105, 225, 1d)),
    SADDLEBROWN(new Color(139, 69, 19, 1d)),
    SALMON(new Color(250, 128, 114, 1d)),
    SANDYBROWN(new Color(244, 164, 96, 1d)),
    SEAGREEN(new Color(46, 139, 87, 1d)),
    SEASHELL(new Color(255, 245, 238, 1d)),
    SIENNA(new Color(160, 82, 45, 1d)),
    SILVER(new Color(192, 192, 192, 1d)),
    SKYBLUE(new Color(135, 206, 235, 1d)),
    SLATEBLUE(new Color(106, 90, 205, 1d)),
    SLATEGRAY(new Color(112, 128, 144, 1d)),
    SLATEGREY(new Color(112, 128, 144, 1d)),
    SNOW(new Color(255, 250, 250, 1d)),
    SPRINGGREEN(new Color(0, 255, 127, 1d)),
    STEELBLUE(new Color(70, 130, 180, 1d)),
    TAN(new Color(210, 180, 140, 1d)),
    TEAL(new Color(0, 128, 128, 1d)),
    THISTLE(new Color(216, 191, 216, 1d)),
    TOMATO(new Color(255, 99, 71, 1d)),
    TURQUOISE(new Color(64, 224, 208, 1d)),
    VIOLET(new Color(238, 130, 238, 1d)),
    WHEAT(new Color(245, 222, 179, 1d)),
    WHITE(new Color(255, 255, 255, 1d)),
    WHITESMOKE(new Color(245, 245, 245, 1d)),
    YELLOW(new Color(255, 255, 0, 1d)),
    YELLOWGREEN(new Color(154, 205, 50, 1d));

    private final Color colorValue;

    private Colors(Color colorValue) {
        this.colorValue = colorValue;
    }

    public Color getColorValue() {
        return this.colorValue;
    }

}
