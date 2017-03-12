def map(val, in_min, in_max, out_min, out_max):
	return (((val - in_min) * (out_max - out_min)) / (in_max - in_min)) + out_min

if __name__ == '__main__':
	theta = 280
	input_range_start_degree =  280 # increasing from left to right
	input_start = 0
	input_end = 70
	output_start = 70
	output_end = 150

	value = theta - input_range_start_degree
	right_motor_intensity = map(value, input_start, input_end, output_start, output_end)

	print("left motor intensity: {}".format(output_end - (right_motor_intensity - output_start)))
	print("right motor intensity: {}".format(right_motor_intensity))