Map {
  background-color: rgba(0,0,0,0);
}

#feeds {
  polygon-opacity: 0.5;
  line-width: 0.5;
  line-color: #000;
}

// expired
#feeds[status = -1] {
  polygon-fill: #e00;
}
  
// about to expire (within 2 mos)
#feeds[status = 0] {
  polygon-fill: #ab3;
}

// ok
#feeds[status = 1] {
  polygon-fill: #44f;
}

// hide above 10
#feeds[zoom <= 7] {
  polygon-fill: rgba(0,0,0,0);
}

// feed labels at or above zoom 9
#feeds[zoom >= 10]::labels {
  text-name: "[agencyname]";
  text-face-name: "Ubuntu Bold";
  text-placement: interior;
  text-halo-radius: 1.5;
}

#metros {
  line-width:0;
  polygon-opacity:0.7;
  polygon-fill:#1d0;
}

// hide below 7, but leave labels until they resolve to
// agency labels at zoom 4
#metros[zoom > 7] {
  polygon-fill: rgba(0,0,0,0);
}

// metro area labels between zoom 6 and 9
#metros::labels[zoom >= 7][zoom < 10] {
  text-name: "[name]";
  text-face-name: "Ubuntu Bold";
  text-placement: interior;
  text-halo-radius: 1.5;
}
