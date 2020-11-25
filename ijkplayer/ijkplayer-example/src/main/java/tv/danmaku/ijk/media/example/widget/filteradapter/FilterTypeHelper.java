package tv.danmaku.ijk.media.example.widget.filteradapter;

import com.xmly.media.camera.view.utils.XMFilterType;

import tv.danmaku.ijk.media.example.R;

public class FilterTypeHelper {

	public static int FilterType2Color(XMFilterType filterType){
		switch (filterType) {
			case NONE:
				return R.color.filter_color_grey_light;
			case FILTER_BEAUTY:
				return R.color.filter_color_pink;
			default:
				return R.color.filter_color_red;
		}
	}

	public static int FilterType2Name(XMFilterType filterType) {
		switch (filterType) {
			case NONE:
				return R.string.filter_none;
			case FILTER_BEAUTY:
				return R.string.filter_beauty;
			case FILTER_SKETCH:
				return R.string.filter_sketch;
			case FILTER_SEPIA:
				return R.string.filter_sepia;
			case FILTER_INVERT:
				return R.string.filter_invert;
			case FILTER_VIGNETTE:
				return R.string.filter_vignette;
			case FILTER_LAPLACIAN:
				return R.string.filter_laplacian;
			case FILTER_GLASS_SPHERE:
				return R.string.filter_glass_sphere;
			case FILTER_CRAYON:
				return R.string.filter_crayon;
			case FILTER_MIRROR:
				return R.string.filter_mirror;
			case FILTER_BEAUTY_OPTIMIZATION:
				return R.string.filter_beauty_optimization;
			case FILTER_IMAGE_SWITCH:
				return R.string.filter_image_switch;
			case FILTER_BLINDS:
				return R.string.filter_blinds;
			case FILTER_FADE_IN_OUT:
				return R.string.filter_fade_in_out;
			default:
				return R.string.filter_none;
		}
	}
}
