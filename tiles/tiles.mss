Map {
  background-color: rgba(0,0,0,0);
}

#metros {
  line-width:0;
  polygon-opacity:0.7;
  polygon-fill:#1d0;
}
  
#feeds {
  line-width:0;
  polygon-opacity:0.5;
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

// hide below 10
#metros[zoom > 7] {
  polygon-fill: rgba(0,0,0,0);
}

// hide above 10
#feeds[zoom <= 7] {
  polygon-fill: rgba(0,0,0,0);
}
  
