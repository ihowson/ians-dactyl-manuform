OPENSCAD := /Applications/OpenSCAD.app/Contents/MacOS/OpenSCAD

STL_TARGETS := things/right.stl things/left.stl things/wedge.stl

# Only regenerates SCAD files. Useful for interactive editing with OpenSCAD GUI.
# quick:

.SCAD_FLAG: src/dactyl_keyboard/dactyl.clj
	lein run src/dactyl_keyboard/dactyl.clj
	touch .SCAD_FLAG

stl: ${STL_TARGETS}
scad: .SCAD_FLAG

things/%.scad: .SCAD_FLAG

things/%.stl: things/%.scad
	${OPENSCAD} -o $@ $<
