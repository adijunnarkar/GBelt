def map(val, in_min, in_max, out_min, out_max):
	return (((val - in_min) * (out_max - out_min)) / (in_max - in_min)) + out_min

if __name__ == '__main__':
	theta = 55
	input_start = 0
	input_end = 70
	output_start = 0
	output_end = 128
	value = theta - 10
	right_motor_intensity = map(value, input_start, input_end, output_start, output_end)

	print("left motor intensity: {}".format(output_end - right_motor_intensity))
	print("right motor intensity: {}".format(right_motor_intensity))