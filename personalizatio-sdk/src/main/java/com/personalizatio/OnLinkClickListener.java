package com.personalizatio;

/**
 * Колбек на клик по ссылке.
 * Все методы должны возвращать Boolean: true - если нужно вызвать открытие средствами самой SDK
 */
public interface OnLinkClickListener {

	/**
	 * Вызывается при клике по обычной ссылке или диплинку
	 * @param url String
	 * @return boolean
	 */
	public boolean onClick(String url);

	/**
	 * Вызывается при клике по товару
	 * @param product Product
	 * @return boolean
	 */
	public boolean onClick(Product product);
}
