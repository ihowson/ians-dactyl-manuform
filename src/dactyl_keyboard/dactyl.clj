; to build
; cd ~/Sync/dactyl-keyboard
; lein repl
; > (load-file "src/dactyl_keyboard/dactyl.clj")

; or
; make stl   # full slow build of all SCAD and STL files
; make scad  # quick build of SCAD files only


(ns dactyl-keyboard.dactyl
  (:refer-clojure :exclude [use import])
  (:require [clojure.core.matrix :refer [array matrix mmul]]
            [scad-clj.scad :refer :all]
            [scad-clj.model :refer :all]
            [unicode-math.core :refer :all]))


(defn deg2rad [degrees]
  (* (/ degrees 180) pi))

;;;;;;;;;;;;;;;;;;;;;;
;; Shape parameters ;;
;;;;;;;;;;;;;;;;;;;;;;

(def nrows 5)
(def ncols 6)

(def α (/ π 12))                        ; curvature of the columns
(def β (/ π 36))                        ; curvature of the rows
(def centerrow (- nrows 3))             ; controls front-back tilt
(def centercol 3)                       ; controls left-right tilt / tenting (higher number is more tenting)
; original
; (def tenting-angle (/ π 12))            ; or, change this for more precise tenting control
; ian: i like more tilt (and will add more after print, but want to make space for thumb cluster)
; v3; about 62mm high
; (def tenting-angle (/ π 6))            ; or, change this for more precise tenting control
; v4; about 71mm high
; (def tenting-angle (/ π 4.5))            ; or, change this for more precise tenting control
; v4; about 71mm high
; v5: leave it the same, but print some big wedges to glue underneath. it's too hard to assemble if it's all one unit.
(def tenting-angle (/ π 5))            ; or, change this for more precise tenting control

(def column-style 
  (if (> nrows 5) :orthographic :standard))  ; options include :standard, :orthographic, and :fixed
; (def column-style :fixed)

(defn column-offset [column]
  (cond
    (= column 2) [0 2.82 -4.5]
    ; pinkie height
    ; original
    ; (>= column 4) [0 -12 5.64]            ; original [0 -5.8 5.64]
    ; ian: raise pinkie columns a fraction
    ; v2
    (= column 4) [0 -12 6.64]
    (= column 5) [0 -12 7.64]
    ; v3
    ; (>= column 4) [0 -12 7.64]
    :else [0 0 0]))

; original
; (def thumb-offsets [16 -3 7])
; ian: push it lower, tilt it more
(def thumb-offsets [12 -3 0])
; (def thumb-offsets [12 3 0])

; original
; (def keyboard-z-offset 9)               ; controls overall height; original=9 with centercol=3; use 16 for centercol=2
; ian: want lowest profile possible
; (def keyboard-z-offset 2.7)               ; controls overall height; original=9 with centercol=3; use 16 for centercol=2
; (def keyboard-z-offset 1.7)               ; controls overall height; original=9 with centercol=3; use 16 for centercol=2
; (def keyboard-z-offset 3.9)               ; pi / 8 tilt controls overall height; original=9 with centercol=3; use 16 for centercol=2
; (def keyboard-z-offset 10)               ; pi / 7 tilt controls overall height; original=9 with centercol=3; use 16 for centercol=2
(def keyboard-z-offset 15)               ; pi / 7 tilt controls overall height; original=9 with centercol=3; use 16 for centercol=2

(def extra-width 2.5)                   ; extra space between the base of keys; original= 2
(def extra-height 1.0)                  ; original= 0.5

; TODO: the top part is way too big and just makes thing large; see if you can reduce it
(def wall-z-offset -7)                 ; length of the first downward-sloping part of the wall (negative)
(def wall-xy-offset 5)                  ; offset in the x and/or y direction for the first downward-sloping part of the wall (negative)
(def wall-thickness 3)                  ; wall thickness parameter; originally 5

;; Settings for column-style == :fixed 
;; The defaults roughly match Maltron settings
;;   http://patentimages.storage.googleapis.com/EP0219944A2/imgf0002.png
;; Fixed-z overrides the z portion of the column ofsets above.
;; NOTE: THIS DOESN'T WORK QUITE LIKE I'D HOPED.
(def fixed-angles [(deg2rad 10) (deg2rad 10) 0 0 0 (deg2rad -15) (deg2rad -15)])  
(def fixed-x [-41.5 -22.5 0 20.3 41.4 65.5 89.6])  ; relative to the middle finger
(def fixed-z [12.1    8.3 0  5   10.7 14.5 17.5])  
(def fixed-tenting (deg2rad 0))  

;;;;;;;;;;;;;;;;;;;;;;;
;; General variables ;;
;;;;;;;;;;;;;;;;;;;;;;;

(def lastrow (dec nrows))
(def cornerrow (dec lastrow))
(def lastcol (dec ncols))

;;;;;;;;;;;;;;;;;
;; Switch Hole ;;
;;;;;;;;;;;;;;;;;

; 14.1 works fine. they could be a fraction tighter, so 14.00 for this print.
; chocs fit this a little more loosely but it's not a big deal
(def keyswitch-height 14.0)
(def keyswitch-width 14.0)

(def sa-profile-key-height 12.7)
(def choc-profile-key-height 6.7)  ; eyeballed this, but it's close enough given the keycaps i'm using

(def plate-thickness 3.5)
(def mount-width (+ keyswitch-width 3))
(def mount-height (+ keyswitch-height 3))

(def single-plate
  (let [top-wall (->> (cube (+ keyswitch-width 3) 1.5 plate-thickness)
                      (translate [0
                                  (+ (/ 1.5 2) (/ keyswitch-height 2))
                                  (/ plate-thickness 2)]))
        left-wall (->> (cube 1.5 (+ keyswitch-height 3) plate-thickness)
                       (translate [(+ (/ 1.5 2) (/ keyswitch-width 2))
                                   0
                                   (/ plate-thickness 2)]))
        ; TODO: look at this to remove side nubs
        side-nub (->> (binding [*fn* 30] (cylinder 1 2.75))
                      (rotate (/ π 2) [1 0 0])
                      (translate [(+ (/ keyswitch-width 2)) 0 1])
                      (hull (->> (cube 1.5 2.75 plate-thickness)
                                 (translate [(+ (/ 1.5 2) (/ keyswitch-width 2))
                                             0
                                             (/ plate-thickness 2)]))))
        ; plate-half (union top-wall left-wall (with-fn 100 side-nub))]
        plate-half (union top-wall left-wall)]
    (union plate-half
           (->> plate-half
                (mirror [1 0 0])
                (mirror [0 1 0])))))

;;;;;;;;;;;;;;;;
;; SA Keycaps ;;
;;;;;;;;;;;;;;;;

(def sa-length 18.25)
; (def sa-double-length 37.5)
(def sa-double-length 18.25)
(def sa-cap {1 (let [bl2 (/ 18.5 2)
                     m (/ 17 2)
                     key-cap (hull (->> (polygon [[bl2 bl2] [bl2 (- bl2)] [(- bl2) (- bl2)] [(- bl2) bl2]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 0.05]))
                                   (->> (polygon [[m m] [m (- m)] [(- m) (- m)] [(- m) m]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 6]))
                                   (->> (polygon [[6 6] [6 -6] [-6 -6] [-6 6]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 12])))]
                 (->> key-cap
                      (translate [0 0 (+ 5 plate-thickness)])
                      (color [220/255 163/255 163/255 1])))
             2 (let [bl2 (/ sa-double-length 2)
                     bw2 (/ 18.25 2)
                     key-cap (hull (->> (polygon [[bw2 bl2] [bw2 (- bl2)] [(- bw2) (- bl2)] [(- bw2) bl2]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 0.05]))
                                   (->> (polygon [[6 16] [6 -16] [-6 -16] [-6 16]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 12])))]
                 (->> key-cap
                      (translate [0 0 (+ 5 plate-thickness)])
                      (color [127/255 159/255 127/255 1])))
             1.5 (let [bl2 (/ 18.25 2)
                       bw2 (/ 28 2)
                       key-cap (hull (->> (polygon [[bw2 bl2] [bw2 (- bl2)] [(- bw2) (- bl2)] [(- bw2) bl2]])
                                          (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                          (translate [0 0 0.05]))
                                     (->> (polygon [[11 6] [-11 6] [-11 -6] [11 -6]])
                                          (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                          (translate [0 0 12])))]
                   (->> key-cap
                        (translate [0 0 (+ 5 plate-thickness)])
                        (color [240/255 223/255 175/255 1])))})

;;;;;;;;;;;;;;;;
;; SA Keycaps ;;
;;;;;;;;;;;;;;;;

(def choc-length 18.25)
(def choc-cap {1 (let [bl2 (/ 18.5 2)
                     m (/ 17 2)
                     key-cap (hull (->> (polygon [[bl2 bl2] [bl2 (- bl2)] [(- bl2) (- bl2)] [(- bl2) bl2]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 0.05]))
                                   (->> (polygon [[m m] [m (- m)] [(- m) (- m)] [(- m) m]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 3]))
                                   (->> (polygon [[6 6] [6 -6] [-6 -6] [-6 6]])
                                        (extrude-linear {:height 0.1 :twist 0 :convexity 0})
                                        (translate [0 0 6])))]
                 (->> key-cap
                      (translate [0 0 (+ 5 plate-thickness)])
                      (color [163/255 163/255 250/255 1])))})

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Placement Functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(def columns (range 0 ncols))
(def rows (range 0 nrows))

(def cap-top-height (+ plate-thickness sa-profile-key-height))
(def row-radius (+ (/ (/ (+ mount-height extra-height) 2)
                      (Math/sin (/ α 2)))
                   cap-top-height))
(def column-radius (+ (/ (/ (+ mount-width extra-width) 2)
                         (Math/sin (/ β 2)))
                      cap-top-height))
(def column-x-delta (+ -1 (- (* column-radius (Math/sin β)))))
(def column-base-angle (* β (- centercol 2)))

(defn is-choc [column row]
  (or
    (and (= column 5) (.contains [1 2 3] row)))) ; Top row will fit a Cherry switch, and this helps to pull in the width a fraction.

(defn apply-key-geometry [translate-fn rotate-x-fn rotate-y-fn column row shape]
  (let [column-angle (* β (- centercol column))   
        placed-shape (->> shape
                          (translate-fn [0 0 (- row-radius)])
                          (rotate-x-fn  (* α (- centerrow row)))      
                          (translate-fn [0 0 row-radius])
                          (translate-fn [0 0 (- column-radius)])
                          (rotate-y-fn  column-angle)
                          (translate-fn [0 0 column-radius])
                          (translate-fn (column-offset column))
                          (translate-fn (if (is-choc column row) [0 0 6] [0 0 0]))
        )
        column-z-delta (* column-radius (- 1 (Math/cos column-angle)))
        ; column-z-delta (* column-radius (- 30 (Math/cos column-angle)))
        placed-shape-ortho (->> shape
                                (translate-fn [0 0 (- row-radius)])
                                (rotate-x-fn  (* α (- centerrow row)))      
                                (translate-fn [0 0 row-radius])
                                (rotate-y-fn  column-angle)
                                (translate-fn [(- (* (- column centercol) column-x-delta)) 0 column-z-delta])
                                (translate-fn (column-offset column)))
        placed-shape-fixed (->> shape
                                (rotate-y-fn  (nth fixed-angles column))
                                (translate-fn [(nth fixed-x column) 0 (nth fixed-z column)])
                                (translate-fn [0 0 (- (+ row-radius (nth fixed-z column)))])
                                (rotate-x-fn  (* α (- centerrow row)))      
                                (translate-fn [0 0 (+ row-radius (nth fixed-z column))])
                                (rotate-y-fn  fixed-tenting)
                                (translate-fn [0 (second (column-offset column)) 0])
                                )]
    (->> (case column-style
          :orthographic placed-shape-ortho 
          :fixed        placed-shape-fixed
                        placed-shape)
         (rotate-y-fn  tenting-angle)
         (translate-fn [0 0 keyboard-z-offset]))))

(defn key-place [column row shape]
  (apply-key-geometry translate 
    (fn [angle obj] (rotate angle [1 0 0] obj)) 
    (fn [angle obj] (rotate angle [0 1 0] obj)) 
    column row shape))

(defn rotate-around-x [angle position] 
  (mmul 
   [[1 0 0]
    [0 (Math/cos angle) (- (Math/sin angle))]
    [0 (Math/sin angle)    (Math/cos angle)]]
   position))

(defn rotate-around-y [angle position] 
  (mmul 
   [[(Math/cos angle)     0 (Math/sin angle)]
    [0                    1 0]
    [(- (Math/sin angle)) 0 (Math/cos angle)]]
   position))

(defn key-position [column row position]
  (apply-key-geometry (partial map +) rotate-around-x rotate-around-y column row position))


(def key-holes
  (apply union
         (for [column columns
               row rows
               :when (or (.contains [2 3] column)
                         (not= row lastrow))]
           (->> single-plate
                (key-place column row)))))

(def caps
  (apply union
         (for [column columns
               row rows
               :when (or (.contains [2 3] column)
                         (not= row lastrow))]
           (->> (if (is-choc column row) (choc-cap 1) (sa-cap 1))  ; CHOCMOD
                (key-place column row)))))


;;;;;;;;;;;;;;;;;;;;
;; Web Connectors ;;
;;;;;;;;;;;;;;;;;;;;

(def web-thickness 3.5)
(def post-size 0.1)
(def web-post (->> (cube post-size post-size web-thickness)
                   (translate [0 0 (+ (/ web-thickness -2)
                                      plate-thickness)])))

(def post-adj (/ post-size 2))
(def web-post-tr (translate [(- (/ mount-width 2) post-adj) (- (/ mount-height 2) post-adj) 0] web-post))
(def web-post-tl (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height 2) post-adj) 0] web-post))
(def web-post-bl (translate [(+ (/ mount-width -2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post))
(def web-post-br (translate [(- (/ mount-width 2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post))

(defn triangle-hulls [& shapes]
  (apply union
         (map (partial apply hull)
              (partition 3 1 shapes))))

(def connectors
  (apply union
         (concat
          ;; Row connections
          (for [column (range 0 (dec ncols))
                row (range 0 lastrow)]
            (triangle-hulls
             (key-place (inc column) row web-post-tl)
             (key-place column row web-post-tr)
             (key-place (inc column) row web-post-bl)
             (key-place column row web-post-br)))

          ;; Column connections
          (for [column columns
                row (range 0 cornerrow)]
            (triangle-hulls
             (key-place column row web-post-bl)
             (key-place column row web-post-br)
             (key-place column (inc row) web-post-tl)
             (key-place column (inc row) web-post-tr)))

          ;; Diagonal connections
          (for [column (range 0 (dec ncols))
                row (range 0 cornerrow)]
            (triangle-hulls
             (key-place column row web-post-br)
             (key-place column (inc row) web-post-tr)
             (key-place (inc column) row web-post-bl)
             (key-place (inc column) (inc row) web-post-tl))))))

;;;;;;;;;;;;
;; Thumbs ;;
;;;;;;;;;;;;

(def thumborigin 
  (map + (key-position 1 cornerrow [(/ mount-width 2) (- (/ mount-height 2)) 0])
         thumb-offsets))

; it's about 38 degrees total range of motion, so 9.5 degrees per modifier. call it 10

(defn thumb-tr-place [shape]
  (->> shape
       (rotate (deg2rad  25) [1 0 0])
       (rotate (deg2rad -30) [0 1 0])
       (rotate (deg2rad  3) [0 0 1])
       (translate thumborigin)
       (translate [-17 -9 4])
       ))

(defn thumb-tl-place [shape]
  (->> shape
       (rotate (deg2rad  15) [1 0 0])
       (rotate (deg2rad -24) [0 1 0])
       (rotate (deg2rad 32.3) [0 0 1])

       (translate thumborigin)
       (translate [-39 -13 -6])))

(defn thumb-ml-place [shape]
  (->> shape
       (rotate (deg2rad  10) [1 0 0])
       (rotate (deg2rad -20) [0 1 0])
       (rotate (deg2rad  51) [0 0 1])
       (translate thumborigin)
       (translate [-56 -25 -14])))

(defn thumb-bl-place [shape]
  (->> shape
       (rotate (deg2rad  5) [1 0 0])
       (rotate (deg2rad -16) [0 1 0])
       (rotate (deg2rad  70) [0 0 1])
       (translate thumborigin)
       ; NOTE: choc
       (translate [-68 -42 -22])
       ))

; duplicate missing defns so we don't need to redo the mesh
(defn thumb-br-place [shape]
  (thumb-bl-place shape)
)

(defn thumb-mr-place [shape]
  (thumb-ml-place shape)
)

(defn thumb-1x-layout [shape]
  (union
   (thumb-tr-place shape)
   (thumb-tl-place shape)
   (thumb-ml-place shape)
   (thumb-bl-place shape)))

(defn thumb-cap-shapes [shape]
  (union
   (thumb-tr-place (sa-cap 1))
   (thumb-tl-place (sa-cap 1))
   (thumb-ml-place (sa-cap 1))
   (thumb-bl-place (sa-cap 1))))

(defn thumb-15x-layout [shape] ())

; (def larger-plate
;   (let [plate-height (/ (- sa-double-length mount-height) 3)
;         top-plate (->> (cube mount-width plate-height web-thickness)
;                        (translate [0 (/ (+ plate-height mount-height) 2)
;                                    (- plate-thickness (/ web-thickness 2))]))
;         ]
;     (union top-plate (mirror [0 1 0] top-plate))))

(def thumbcaps
  (union
   (thumb-cap-shapes 1)
   (thumb-15x-layout (rotate (/ π 2) [0 0 1] (sa-cap 1.5)))))


(def thumb
  (union
   (thumb-1x-layout single-plate)
  ;  (thumb-15x-layout single-plate)
  ;  (thumb-15x-layout larger-plate)
   ))

; (def fuss 6.5)
(def fuss 6.4)  ; keyswitch_height / 2 ?

(def thumb-post-tr (translate [(- (/ mount-width 2) post-adj)  (- (/ mount-height 1.15) post-adj fuss) 0] web-post))
(def thumb-post-space-tr (translate [(- (/ mount-width 2) post-adj)  (- (/ mount-height 0.85) post-adj fuss) 0] web-post))
; v1
(def thumb-post-tl (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height  1.15) post-adj fuss) 0] web-post))
(def thumb-post-space-tl (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height 0.85) post-adj fuss) 0] web-post))
; v3
; (def thumb-post-tl (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height  0.9) post-adj fuss) 0] web-post))
(def thumb-post-bl (translate [(+ (/ mount-width -2) post-adj) (+ (/ mount-height -1.15) post-adj fuss) 0] web-post))
(def thumb-post-br (translate [(- (/ mount-width 2) post-adj)  (+ (/ mount-height -1.15) post-adj fuss) 0] web-post))

(def thumb-connectors
  (union
      (triangle-hulls    ; top two -- joins top two thumb switches
             (thumb-tl-place thumb-post-tr)
             (thumb-tl-place thumb-post-br)
             (thumb-tr-place thumb-post-tl)
             (thumb-tr-place thumb-post-bl))
      (triangle-hulls    ; bottom two on the left -- joins bottom two thumb switches
             (thumb-bl-place web-post-tr)
             (thumb-bl-place web-post-br)
             (thumb-ml-place web-post-tl)
             (thumb-ml-place web-post-bl))

      (triangle-hulls    ; top two to the middle two, starting on the left
             (thumb-tl-place thumb-post-tl)
             (thumb-ml-place web-post-tr)
             (thumb-tl-place thumb-post-bl)
             (thumb-ml-place web-post-br)
            ;  (thumb-tl-place thumb-post-br) ; a ; this could use some tweaking to fill the gap
            ;  (thumb-tl-place thumb-post-bl) ; this overlaps the key BUT it fills the remaining gap
            ;  (thumb-tl-place thumb-post-br) ; this overlaps the key BUT it fills the remaining gap
            ;  (thumb-tr-place thumb-post-br) ; a
            ;  (thumb-tr-place web-post-bl)

             ; v1
            ;  (thumb-tr-place thumb-post-bl) ; this overlaps the key BUT it fills the remaining gap
            ;  (thumb-mr-place web-post-tr) ; a  ; this overlaps the key
            ;  (thumb-tr-place thumb-post-bl) ; this overlaps the key BUT it fills the remaining gap
            ;  (thumb-tr-place thumb-post-bl) ; this overlaps the key BUT it fills the remaining gap
            ;  (thumb-tr-place thumb-post-tr) ; this overlaps the key BUT it fills the remaining gap
            ;  (thumb-mr-place web-post-br) ; a ; MAYBE
      )
      (triangle-hulls
             (thumb-tl-place thumb-post-tl)
             (thumb-tl-place thumb-post-tr)
             (thumb-tl-place web-post-tl)
             (thumb-tl-place web-post-tr))
      (triangle-hulls    ; top two to the main keyboard, starting on the left
      ; UP TO HERE
    ;  (left-key-place cornerrow -1 web-post)
    ;  (left-key-place cornerrow -1 (translate (wall-locate1 -1 0) web-post))
    ;  (left-key-place cornerrow -1 (translate (wall-locate2 -1 0) web-post))
    ;  (left-key-place cornerrow -1 (translate (wall-locate3 -1 0) web-post))
    ;  (thumb-tl-place thumb-post-tl)
            ;  (thumb-tl-place thumb-post-space-tl)
             (thumb-tl-place thumb-post-space-tr)
             (thumb-tl-place thumb-post-tr)
             (key-place 0 cornerrow web-post-bl)
             (thumb-tr-place web-post-tl)
            ;  (thumb-tr-place web-post-tr)
             (key-place 0 cornerrow web-post-br)
             (thumb-tr-place web-post-tr)
            ;  (key-place 1 cornerrow web-post-tl)
            ;  (key-place 2 lastrow web-post-tl)
            ;  (key-place 1 cornerrow web-post-bl)
            ;  (thumb-tr-place thumb-post-tr)
             (key-place 1 cornerrow web-post-br)
             (key-place 2 lastrow web-post-tl)
             (thumb-tr-place thumb-post-tr)
             (thumb-tr-place thumb-post-br)
             (key-place 2 lastrow web-post-tl)
             (key-place 2 lastrow web-post-bl)
            ;  (thumb-tr-place thumb-post-tr)
             (key-place 2 lastrow web-post-bl)
             (thumb-tr-place thumb-post-br)
             (key-place 2 lastrow web-post-br)
             (key-place 3 lastrow web-post-bl)
             (key-place 2 lastrow web-post-tr)
             (key-place 3 lastrow web-post-tl)
             (key-place 3 cornerrow web-post-bl)
             (key-place 3 lastrow web-post-tr)
             (key-place 3 cornerrow web-post-br)
             (key-place 4 cornerrow web-post-bl)
             )
      (triangle-hulls 
             (key-place 1 cornerrow web-post-br)
             (key-place 2 lastrow web-post-tl)
             (key-place 2 cornerrow web-post-bl)
             (key-place 2 lastrow web-post-tr)
             (key-place 2 cornerrow web-post-br)
             (key-place 3 cornerrow web-post-bl)
             )
      (triangle-hulls 
             (key-place 3 lastrow web-post-tr)
             (key-place 3 lastrow web-post-br)
             (key-place 3 lastrow web-post-tr)
             (key-place 4 cornerrow web-post-bl))
  ))

;;;;;;;;;;
;; Case ;;
;;;;;;;;;;

(defn bottom [height p]
  (->> (project p)
       (extrude-linear {:height height :twist 0 :convexity 0})
       (translate [0 0 (- (/ height 2) 10)])))

(defn bottom-hull [& p]
  (hull p (bottom 0.001 p)))

(def left-wall-x-offset 10)
(def left-wall-z-offset  4)

(defn left-key-position [row direction]
  (map - (key-position 0 row [(* mount-width -0.5) (* direction mount-height 0.5) 0]) [left-wall-x-offset 0 left-wall-z-offset]) )

(defn left-key-place [row direction shape]
  (translate (left-key-position row direction) shape))


(defn wall-locate1 [dx dy] [(* dx wall-thickness) (* dy wall-thickness) -1])
(defn wall-locate2 [dx dy] [(* dx wall-xy-offset) (* dy wall-xy-offset) wall-z-offset])
(defn wall-locate3 [dx dy] [(* dx (+ wall-xy-offset wall-thickness)) (* dy (+ wall-xy-offset wall-thickness)) wall-z-offset])

(defn wall-brace [place1 dx1 dy1 post1 place2 dx2 dy2 post2]
  (union
    (hull
      (place1 post1)
      (place1 (translate (wall-locate1 dx1 dy1) post1))
      (place1 (translate (wall-locate2 dx1 dy1) post1))
      (place1 (translate (wall-locate3 dx1 dy1) post1))
      (place2 post2)
      (place2 (translate (wall-locate1 dx2 dy2) post2))
      (place2 (translate (wall-locate2 dx2 dy2) post2))
      (place2 (translate (wall-locate3 dx2 dy2) post2)))
    (bottom-hull
      (place1 (translate (wall-locate2 dx1 dy1) post1))
      (place1 (translate (wall-locate3 dx1 dy1) post1))
      (place2 (translate (wall-locate2 dx2 dy2) post2))
      (place2 (translate (wall-locate3 dx2 dy2) post2)))
      ))

(defn key-wall-brace [x1 y1 dx1 dy1 post1 x2 y2 dx2 dy2 post2] 
  (wall-brace (partial key-place x1 y1) dx1 dy1 post1 
              (partial key-place x2 y2) dx2 dy2 post2))

; Originally 1. We thin the right wall to minimize ground clearance. It can be thicker in the center since that's not what's limiting.
(def edge-rwff 0.2) ;; right wall flare factor
(def avg-rwff 0.5)
(def mid-rwff 0.7) ;; right wall flare factor

(def case-walls
  (union
   ; back wall
   (for [x (range 0 ncols)] (key-wall-brace x 0 0 1 web-post-tl x       0 0 1 web-post-tr))
   (for [x (range 1 ncols)] (key-wall-brace x 0 0 1 web-post-tl (dec x) 0 0 1 web-post-tr))

   (key-wall-brace lastcol 0 0 1 web-post-tr
                   lastcol 0 edge-rwff 0 web-post-tr)
   ; right wall
   ; Yes, this is clunky. All I really want is for these edges to converge to a
   ; straight line along the Y axis.

   (key-wall-brace lastcol 0 edge-rwff 0 web-post-tr 
                   lastcol 0 avg-rwff 0 web-post-br) ; key edge

   (key-wall-brace lastcol 0 avg-rwff 0 web-post-br
                   lastcol 1 edge-rwff 0 web-post-tr) ; tweener

   (key-wall-brace lastcol 1 edge-rwff 0 web-post-tr
                   lastcol 1 avg-rwff 0 web-post-br) ; edge

   (key-wall-brace lastcol 1 avg-rwff 0 web-post-br
                   lastcol 2 avg-rwff 0 web-post-tr) ; tweener

   (key-wall-brace lastcol 2 avg-rwff 0 web-post-tr 
                   lastcol 2 avg-rwff 0 web-post-br) ; edge

   (key-wall-brace lastcol 2 avg-rwff 0 web-post-br
                   lastcol 3 avg-rwff 0 web-post-tr) ; tweener

   (key-wall-brace lastcol 3 avg-rwff 0 web-post-tr 
                   lastcol 3 edge-rwff 0 web-post-br) ; edge

   (key-wall-brace lastcol cornerrow 0        -1 web-post-br
                   lastcol cornerrow edge-rwff 0 web-post-br) ; bottom right corner

   ; left wall
   (for [y (range 0 lastrow)] (union (wall-brace (partial left-key-place y 1)       -1 0 web-post (partial left-key-place y -1) -1 0 web-post)
                                     (hull (key-place 0 y web-post-tl)
                                           (key-place 0 y web-post-bl)
                                           (left-key-place y  1 web-post)
                                           (left-key-place y -1 web-post))))
   (for [y (range 1 lastrow)] (union (wall-brace (partial left-key-place (dec y) -1) -1 0 web-post (partial left-key-place y  1) -1 0 web-post)
                                     (hull (key-place 0 y       web-post-tl)
                                           (key-place 0 (dec y) web-post-bl)
                                           (left-key-place y        1 web-post)
                                           (left-key-place (dec y) -1 web-post))))
   (wall-brace (partial key-place 0 0) 0 1 web-post-tl (partial left-key-place 0 1) 0 1 web-post)
   (wall-brace (partial left-key-place 0 1) 0 1 web-post (partial left-key-place 0 1) -1 0 web-post)
   ; front wall
   (key-wall-brace lastcol 0 0 1 web-post-tr lastcol 0 edge-rwff 0 web-post-tr)
   (key-wall-brace 3 lastrow   0 -1 web-post-bl 3 lastrow 0.5 -1 web-post-br)
   (key-wall-brace 3 lastrow 0.5 -1 web-post-br 4 cornerrow 1 -1 web-post-bl)
   (for [x (range 4 ncols)] (key-wall-brace x cornerrow 0 -1 web-post-bl x       cornerrow 0 -1 web-post-br))
   (for [x (range 5 ncols)] (key-wall-brace x cornerrow 0 -1 web-post-bl (dec x) cornerrow 0 -1 web-post-br))
   ; thumb walls
  ;  (wall-brace thumb-tr-place  0 -1 web-post-br thumb-tr-place  0 -1 thumb-post-tr)
  ;  (wall-brace thumb-ml-place  0 -1 web-post-br thumb-tl-place  0 -1 thumb-post-bl)
   (wall-brace thumb-tl-place  0 1 thumb-post-tl thumb-tl-place  0 1 web-post-tr) ;; this alsomost fixes the gap but it needs to go up top
   (wall-brace thumb-ml-place  0 1 web-post-tr thumb-tl-place  0 1 thumb-post-tl)
   (wall-brace thumb-ml-place  0 -1 web-post-br thumb-tl-place  0 -1 thumb-post-bl)
   (wall-brace thumb-tl-place  0 -1 web-post-br thumb-tr-place  0 -1 thumb-post-bl)
   (wall-brace thumb-tr-place  0 -1 web-post-bl thumb-tr-place  0 -1 thumb-post-br)
   (wall-brace thumb-tl-place  0 -1 web-post-bl thumb-tl-place  0 -1 thumb-post-br)
  ;  (wall-brace thumb-ml-place  0 -1 web-post-br thumb-tl-place  0 -1 thumb-post-bl)
  ;  (wall-brace thumb-mr-place  0 -1 web-post-br thumb-tr-place  0 -1 thumb-post-br)
   (wall-brace thumb-ml-place  0 1 web-post-tl thumb-ml-place  0 1 thumb-post-tr)
   (wall-brace thumb-ml-place  0 -1 web-post-br thumb-ml-place  0 -1 web-post-bl)
   (wall-brace thumb-bl-place  0 -1 web-post-br thumb-bl-place  0 -1 web-post-bl)
   (wall-brace thumb-ml-place -0.3  1 web-post-tr thumb-ml-place  0  1 web-post-tl)
   (wall-brace thumb-bl-place  0  1 web-post-tr thumb-bl-place  0  1 web-post-tl)
   (wall-brace thumb-br-place -1  0 web-post-tl thumb-br-place -1  0 web-post-bl)
   (wall-brace thumb-bl-place -1  0 web-post-tl thumb-bl-place -1  0 web-post-bl)
   ; thumb corners
   (wall-brace thumb-br-place -1  0 web-post-bl thumb-br-place  0 -1 web-post-bl)
   (wall-brace thumb-bl-place -1  0 web-post-tl thumb-bl-place  0  1 web-post-tl)
   ; thumb tweeners
   (wall-brace thumb-mr-place  0.5 -1 web-post-bl thumb-br-place  0 -1 web-post-br) ;; FIXME: this is the crack in the thumb section
   (wall-brace thumb-ml-place  0  1 web-post-tl thumb-bl-place  0  1 web-post-tr)
  ;  (wall-brace thumb-bl-place -1  0 web-post-bl thumb-br-place -1  0 web-post-tl)
   (wall-brace thumb-tr-place  0 -1 thumb-post-br (partial key-place 3 lastrow)  0 -1 web-post-bl)
   ; clunky bit on the top left thumb connection  (normal connectors don't work well)
   (bottom-hull
     (left-key-place cornerrow -1 (translate (wall-locate2 -1 0) web-post))
     (left-key-place cornerrow -1 (translate (wall-locate3 -1 0) web-post))
     (thumb-ml-place (translate (wall-locate2 -0.2 1) web-post-tr))
     (thumb-ml-place (translate (wall-locate3 -0.2 1) web-post-tr)))
   (hull
     (left-key-place cornerrow -1 (translate (wall-locate2 -1 0) web-post))
     (left-key-place cornerrow -1 (translate (wall-locate3 -1 0) web-post))
     (thumb-ml-place (translate (wall-locate2 -0.2 1) web-post-tr))
     (thumb-ml-place (translate (wall-locate3 -0.2 1) web-post-tr))
     (thumb-tl-place thumb-post-space-tl))
   (hull ;; this is a little thin ;; UP TO HERE
     (left-key-place cornerrow -1 web-post)
     (left-key-place cornerrow -1 (translate (wall-locate1 -1 0) web-post))
     (left-key-place cornerrow -1 (translate (wall-locate2 -1 0) web-post))
     (left-key-place cornerrow -1 (translate (wall-locate3 -1 0) web-post))
     (thumb-tl-place thumb-post-space-tl))
   (hull ;; this is a little thin UP TO HERE
     (left-key-place cornerrow -1 web-post)
     (left-key-place cornerrow -1 (translate (wall-locate1 -1 0) web-post))
     (key-place 0 cornerrow web-post-bl)
     (key-place 0 cornerrow (translate (wall-locate1 -0.3 0) web-post-bl))
     (thumb-tl-place thumb-post-space-tl)
     (thumb-tl-place thumb-post-space-tr))
  ;  (hull
  ;    (thumb-ml-place web-post-tr)
  ;    (thumb-ml-place (translate (wall-locate1 -1 1) web-post-tr))
  ;    (thumb-ml-place (translate (wall-locate2 -1 1) web-post-tr))
  ;    (thumb-ml-place (translate (wall-locate3 -1 1) web-post-tr))
  ;    (thumb-tl-place thumb-post-tl))
  ))

; TODO: measure your socket properly so you can have the mag part outside through a 1mm case wall
(def usb-holder-position (key-position 1 0 (map + (wall-locate2 -0.8 1) [0 (/ mount-height 2) 0])))
(def usb-holder-size [8.5 5.5 3.5])
(def usb-holder-size-pad [8.5 5.6 3.5])
(def usb-holder-thickness 0)

; need an 8x3mm hole for the usb-stick-through; 1mm deep
; micro is 18mm across, 4mm from top of usb to bottom of pcb
(def usb-holder
    (->> (cube (+ (first usb-holder-size) usb-holder-thickness) (second usb-holder-size) (+ (last usb-holder-size) usb-holder-thickness))
         (translate [(first usb-holder-position) (second usb-holder-position) (/ (+ 9 (last usb-holder-size) usb-holder-thickness) 1.5)])))
(def usb-holder-hole
    (->> (apply cube usb-holder-size-pad)
         (translate [(first usb-holder-position) (second usb-holder-position) (/ (+ 9 (last usb-holder-size) usb-holder-thickness) 1.5)])))
(def micro-hull-cutout
    (->> (apply cube [18.1 5.5 6.5])
         (translate [(first usb-holder-position) (- (second usb-holder-position) 2.5) (/ (+ 9 (last usb-holder-size) usb-holder-thickness) 1.5)])))

(def tent-platform
  (union (->> (cube 12 12 3)
              (translate [-53 52 1.5]))
         (->> (cube 12 12 3)
              (translate [-87 -60 1.5]))
))

; (defn screw-insert [column row bottom-radius top-radius height] 
;   (let [shift-right   (= column lastcol)
;         shift-left    (= column 0)
;         shift-up      (and (not (or shift-right shift-left)) (= row 0))
;         shift-down    (and (not (or shift-right shift-left)) (>= row lastrow))
;         position      (if shift-up     (key-position column row (map + (wall-locate2  0  1) [0 (/ mount-height 2) 0]))
;                        (if shift-down  (key-position column row (map - (wall-locate2  0 -1) [0 (/ mount-height 2) 0]))
;                         (if shift-left (map + (left-key-position row 0) (wall-locate3 -1 0)) 
;                                        (key-position column row (map + (wall-locate2  1  0) [(/ mount-width 2) 0 0])))))
;         ]
;     (->> (screw-insert-shape bottom-radius top-radius height)
;          (translate [(first position) (second position) (/ height 2)])
;     )))

; (defn screw-insert-all-shapes [bottom-radius top-radius height]
;   (union (->> (screw-insert 0 0         bottom-radius top-radius height)
;               (translate [3 0 0]))

; trrs box is 6x5mm, barrel is 5mm dia and 2mm deep
(def trrs-hole
  (union
    (->> (union (binding [*fn* 36] (cylinder [2.70 2.70] 8)))  ; little bit of kerf
         (rotate (/ π 2) [1 0 0])
         (translate [(+ -15 (first usb-holder-position)) (- (second usb-holder-position) 1) (/ (+ 12 (last usb-holder-size) usb-holder-thickness) 2)]))
    ; hold the box
    (->> (union (apply cube [6.4 4.90 5.4]))  ;depth here matters; has been eyeballed
         (translate [(+ -15 (first usb-holder-position)) (- (second usb-holder-position) 2) (/ (+ 12 (last usb-holder-size) usb-holder-thickness) 2)]))
    )
)

(def reset-hole
    (union
      (->> (union (binding [*fn* 36] (cylinder [3.5 3.5] 8)))
          (rotate (/ π 2) [1 0 0])
          (translate [(+ -6 (first usb-holder-position)) (- (second usb-holder-position) 1) (/ (+ 60 (last usb-holder-size) usb-holder-thickness) 2)]))
      ; thinner wall
      (->> (union (binding [*fn* 36] (cylinder [5 5] 5))) ;; depth here matters; has been eyeballed
          (rotate (/ π 2) [1 0 0])
          (translate [(+ -6 (first usb-holder-position)) (- (second usb-holder-position) 2) (/ (+ 60 (last usb-holder-size) usb-holder-thickness) 2)]))
    )
)

(def teensy-width 30)  
(def teensy-height 12)
(def teensy-length 33)
; (def teensy2-length 43)
(def teensy-pcb-thickness 2) 
(def teensy-holder-width  (+ 7 teensy-pcb-thickness))
(def teensy-holder-height (+ 6 teensy-width))
(def teensy-offset-height 5)
(def teensy-holder-top-length 18)
(def teensy-top-xy (key-position 0 (- centerrow 1) (wall-locate3 -1 0)))
(def teensy-bot-xy (key-position 0 (+ centerrow 1) (wall-locate3 -1 0)))
(def teensy-holder-length (- (second teensy-top-xy) (second teensy-bot-xy)))
(def teensy-holder-offset (/ teensy-holder-length -2))
(def teensy-holder-top-offset (- (/ teensy-holder-top-length 2) teensy-holder-length))
 
(def teensy-holder 
    (->> 
        (union 
          (->> (cube 3 teensy-holder-length (+ 6 teensy-width))
               (translate [1.5 teensy-holder-offset 0]))
          (->> (cube teensy-pcb-thickness teensy-holder-length 3)
               (translate [(+ (/ teensy-pcb-thickness 2) 3) teensy-holder-offset (- -1.5 (/ teensy-width 2))]))
          (->> (cube 4 teensy-holder-length 4)
               (translate [(+ teensy-pcb-thickness 5) teensy-holder-offset (-  -1 (/ teensy-width 2))]))
          (->> (cube teensy-pcb-thickness teensy-holder-top-length 3)
               (translate [(+ (/ teensy-pcb-thickness 2) 3) teensy-holder-top-offset (+ 1.5 (/ teensy-width 2))]))
          (->> (cube 4 teensy-holder-top-length 4)
               (translate [(+ teensy-pcb-thickness 5) teensy-holder-top-offset (+ 1 (/ teensy-width 2))])))
        (translate [(- teensy-holder-width) 0 0])
        (translate [-1.4 0 0])
        (translate [(first teensy-top-xy) 
                    (- (second teensy-top-xy) 1) 
                    (/ (+ 6 teensy-width) 2)])
           ))

(defn screw-insert-shape [bottom-radius top-radius height] 
   (union (cylinder [bottom-radius top-radius] height)
          (translate [0 0 (/ height 2)] (sphere top-radius))))

(defn screw-insert [column row bottom-radius top-radius height] 
  (let [shift-right   (= column lastcol)
        shift-left    (= column 0)
        shift-up      (and (not (or shift-right shift-left)) (= row 0))
        shift-down    (and (not (or shift-right shift-left)) (>= row lastrow))
        position      (if shift-up     (key-position column row (map + (wall-locate2  0  1) [0 (/ mount-height 2) 0]))
                       (if shift-down  (key-position column row (map - (wall-locate2  0 -1) [0 (/ mount-height 2) 0]))
                        (if shift-left (map + (left-key-position row 0) (wall-locate3 -1 0)) 
                                       (key-position column row (map + (wall-locate2  1  0) [(/ mount-width 2) 0 0])))))
        ]
    (->> (screw-insert-shape bottom-radius top-radius height)
         (translate [(first position) (second position) (/ height 2)])
    )))

(defn screw-insert-all-shapes [bottom-radius top-radius height]
  (union (->> (screw-insert 0 0         bottom-radius top-radius height)
              (translate [3 0 0]))
         (->> (screw-insert 0 lastrow   bottom-radius top-radius height) ;; in wrong place
              (translate [-10.5 0 0]))
         (->> (screw-insert 1 lastrow  bottom-radius top-radius height)
              (translate [0 4 0]))
         (->> (screw-insert 3 0         bottom-radius top-radius height)
              (translate [0 0 0]))
         (->> (screw-insert (+ lastcol -0.1) -0.4  bottom-radius top-radius height) ; UP to here
              (translate [0 -9 0]))
         (->> (screw-insert (+ lastcol -0.1) (+ lastrow -0.5) bottom-radius top-radius height) ; UP to here
              (translate [0 -13 0]))
        ;  (screw-insert (+ lastcol 0.3) (+ 1 0.5)  bottom-radius top-radius height) ; UP to here
         ))
(def screw-insert-height 3.8)
(def screw-insert-bottom-radius (/ 5.31 2))
(def screw-insert-top-radius (/ 5.1 2))
(def screw-insert-holes  (screw-insert-all-shapes screw-insert-bottom-radius screw-insert-top-radius screw-insert-height))
(def screw-insert-outers (screw-insert-all-shapes (+ screw-insert-bottom-radius 1.6) (+ screw-insert-top-radius 1.6) (+ screw-insert-height 1.5)))
(def screw-insert-screw-holes  (screw-insert-all-shapes 1.7 1.7 350))

(def wire-post-height 3)
(def wire-post-overhang 3.5)
(def wire-post-diameter 2.6)
(defn wire-post [direction offset]
   (->> (union (translate [0 (* wire-post-diameter -0.5 direction) 0] (cube wire-post-diameter wire-post-diameter wire-post-height))
               (translate [0 (* wire-post-overhang -0.5 direction) (/ wire-post-height -2)] (cube wire-post-diameter wire-post-overhang wire-post-diameter)))
        (translate [0 (- offset) (+ (/ wire-post-height -2) 3) ])
        (rotate (/ α -2) [1 0 0])
        (translate [3 (/ mount-height -2) 0])))

(def wire-posts
  (union
     (thumb-ml-place (translate [-5 0 -2] (wire-post  1 0)))
     (thumb-ml-place (translate [ 0 0 -2.5] (wire-post -1 6)))
     (thumb-ml-place (translate [ 5 0 -2] (wire-post  1 0)))
     (for [column (range 0 lastcol)
           row (range 0 cornerrow)]
       (union
        (key-place column row (translate [-5 0 0] (wire-post 1 0)))
        (key-place column row (translate [0 0 0] (wire-post -1 6)))
        (key-place column row (translate [5 0 0] (wire-post  1 0)))))))

(def side-right
  (union
    key-holes
    connectors
    thumb
    thumb-connectors
    tent-platform
    (difference
      (union
        case-walls
        screw-insert-outers
        usb-holder)
      usb-holder-hole
      micro-hull-cutout
      reset-hole
      screw-insert-holes)
    ;; caps  ; useful for debugging
  )
)

(def model-right
  (difference
    side-right
    (translate [0 0 -20] (cube 350 350 40))
))

(def slice
  (cut (translate [0, 0, -0.01] side-right)
))

(def wedge
  (rotate [(/ pi 2) 0 0]
    ; REMINDER: the entire slice must be on the right-hand-side of the X axis,
    ; or nothing will come out for extrude-rotate.
    ; You should have this touching the axis to minimize stack height.

    ; NOTE: decrease fn for faster rendering. Increase for smoother curves at
    ; output.
    ; Remove fn altogether for dev and it will be much faster.
    ;; (union              (extrude-rotate {:angle -18}
    (binding [*fn* 200] (extrude-rotate {:angle -18}
      (translate [58, 0, 0]
        (rotate [0 0 pi]
          slice)
)))))

(def plate
  (cut
    (translate [0 0 -0.1]
      (difference
        (union case-walls screw-insert-outers)
        screw-insert-screw-holes
))))

(def plate-insulator
  (scale [0.85 0.85 1.0]
  (cut
    (translate [0 0 -0.1]
      (union case-walls screw-insert-outers)
))))

(spit "things/plate.scad" (write-scad plate))
(spit "things/plate-insulator.scad" (write-scad plate-insulator))
(spit "things/right.scad" (write-scad model-right))
(spit "things/left.scad" (write-scad (mirror [-1 0 0] model-right)))
(spit "things/wedge.scad" (write-scad wedge))

(defn -main [dum] 1)  ; dummy to make it easier to batch
